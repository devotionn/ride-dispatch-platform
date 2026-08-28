package com.funccrypto.ridedispatch.driver;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

import com.funccrypto.ridedispatch.audit.AuditService;
import com.funccrypto.ridedispatch.order.OrderStatus;
import com.funccrypto.ridedispatch.order.RideOrderEntity;
import com.funccrypto.ridedispatch.order.RideOrderRepository;
import com.funccrypto.ridedispatch.settlement.DriverAccountEntity;
import com.funccrypto.ridedispatch.settlement.DriverAccountRepository;
import com.funccrypto.ridedispatch.settlement.WithdrawalEntity;
import com.funccrypto.ridedispatch.settlement.WithdrawalRepository;
import com.funccrypto.ridedispatch.shared.error.BusinessException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DriverAdminService {

    private final DriverRepository driverRepository;
    private final VehicleRepository vehicleRepository;
    private final DriverQrShortCodeService qrShortCodeService;
    private final QrCodeRenderer qrCodeRenderer;
    private final RideOrderRepository orderRepository;
    private final DriverAccountRepository accountRepository;
    private final WithdrawalRepository withdrawalRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;
    private final Clock clock;

    public DriverAdminService(
            DriverRepository driverRepository,
            VehicleRepository vehicleRepository,
            DriverQrShortCodeService qrShortCodeService,
            QrCodeRenderer qrCodeRenderer,
            RideOrderRepository orderRepository,
            DriverAccountRepository accountRepository,
            WithdrawalRepository withdrawalRepository,
            PasswordEncoder passwordEncoder,
            AuditService auditService,
            Clock clock) {
        this.driverRepository = driverRepository;
        this.vehicleRepository = vehicleRepository;
        this.qrShortCodeService = qrShortCodeService;
        this.qrCodeRenderer = qrCodeRenderer;
        this.orderRepository = orderRepository;
        this.accountRepository = accountRepository;
        this.withdrawalRepository = withdrawalRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
        this.clock = clock;
    }

    @Transactional
    public DriverView create(CreateDriverCommand command, Long operatorId, String requestId) {
        if (driverRepository.existsByDriverNo(command.driverNo())) {
            throw new BusinessException("DRIVER_NO_DUPLICATE", "司机工号已存在");
        }
        if (vehicleRepository.existsByPlateNo(command.plateNo())) {
            throw new BusinessException("VEHICLE_PLATE_DUPLICATE", "车牌号已存在");
        }

        Instant now = clock.instant();
        String qrShortCode = uniqueShortCode();
        DriverEntity driver = driverRepository.save(DriverEntity.createWithPassword(
                command.driverNo(),
                command.name(),
                command.mobile(),
                passwordEncoder.encode(command.password()),
                command.maxPassengers(),
                command.availablePassengers(),
                qrShortCode,
                now));
        VehicleEntity vehicle = vehicleRepository.save(new VehicleEntity(
                driver.getId(),
                command.plateNo(),
                command.brandModel(),
                command.maxPassengers(),
                now));
        driver.assignDefaultVehicle(vehicle.getId(), now);

        DriverView result = DriverView.from(driver, vehicle);
        auditService.log(
                "ADMIN", operatorId, "DRIVER", driver.getId().toString(), "DRIVER_CREATED",
                null,
                Map.of("driverNo", driver.getDriverNo(), "plateNo", vehicle.getPlateNo()),
                null, requestId, now);
        return result;
    }

    @Transactional(readOnly = true)
    public List<DriverView> list() {
        return driverRepository.findAllByOrderByIdDesc().stream()
                .map(driver -> DriverView.from(
                        driver,
                        driver.getDefaultVehicleId() == null
                                ? null
                                : vehicleRepository.findById(driver.getDefaultVehicleId()).orElse(null)))
                .toList();
    }

    @Transactional(readOnly = true)
    public DriverDetailView detail(Long id) {
        DriverEntity driver = requireDriver(id);
        List<OrderStatus> activeStatuses = List.of(
                OrderStatus.PENDING_DRIVER_CONFIRM,
                OrderStatus.ACCEPTED,
                OrderStatus.IN_SERVICE,
                OrderStatus.PENDING_PAYMENT);
        List<OrderStatus> historyStatuses = List.of(OrderStatus.COMPLETED, OrderStatus.CANCELLED, OrderStatus.EXCEPTION);
        List<RideOrderEntity> activeOrders = orderRepository
                .findByCurrentDriverIdAndStatusInOrderByCreatedAtDesc(driver.getId(), activeStatuses);
        List<RideOrderEntity> historyOrders = orderRepository
                .findByCurrentDriverIdAndStatusInOrderByCreatedAtDesc(driver.getId(), historyStatuses);
        DriverAccountEntity account = accountRepository.findByDriverId(driver.getId()).orElse(null);
        List<WithdrawalEntity> withdrawals = withdrawalRepository.findByDriverIdOrderByCreatedAtDesc(driver.getId());
        return new DriverDetailView(
                view(driver),
                activeOrders.stream().limit(20).map(OrderSummary::from).toList(),
                historyOrders.stream().limit(20).map(OrderSummary::from).toList(),
                orderRepository.countByCurrentDriverIdAndStatus(driver.getId(), OrderStatus.COMPLETED),
                account == null ? 0 : account.getBusinessIncome(),
                account == null ? 0 : account.getAvailableBalance(),
                withdrawals.stream().limit(20).map(WithdrawalSummary::from).toList());
    }

    @Transactional
    public DriverView update(Long id, UpdateDriverCommand command, Long operatorId, String requestId) {
        DriverEntity driver = requireDriver(id);
        VehicleEntity vehicle = driver.getDefaultVehicleId() == null
                ? null
                : vehicleRepository.findById(driver.getDefaultVehicleId()).orElse(null);
        if (vehicle == null) {
            throw new BusinessException("VEHICLE_NOT_FOUND", "司机默认车辆不存在");
        }
        if (vehicleRepository.existsByPlateNoAndIdNot(command.plateNo(), vehicle.getId())) {
            throw new BusinessException("VEHICLE_PLATE_DUPLICATE", "车牌号已存在");
        }
        if (command.maxPassengers() < 1 || command.availablePassengers() < 0
                || command.availablePassengers() > command.maxPassengers()) {
            throw new BusinessException("DRIVER_CAPACITY_INVALID", "司机可接人数配置不合法");
        }

        Instant now = clock.instant();
        Map<String, Object> before = snapshot(driver, vehicle);
        driver.updateProfile(
                command.name(), command.mobile(),
                command.password() == null || command.password().isBlank()
                        ? null : passwordEncoder.encode(command.password()),
                command.maxPassengers(), command.availablePassengers(), now);
        vehicle.update(command.plateNo(), command.brandModel(), command.maxPassengers(), now);
        DriverView result = view(driver, vehicle);
        auditService.log("ADMIN", operatorId, "DRIVER", id.toString(), "DRIVER_UPDATED",
                before, snapshot(driver, vehicle), null, requestId, now);
        return result;
    }

    @Transactional
    public DriverView updateAccountStatus(Long id, DriverAccountStatus accountStatus, Long operatorId, String requestId) {
        if (accountStatus == null) {
            throw new BusinessException("DRIVER_STATUS_INVALID", "司机账号状态不能为空");
        }
        DriverEntity driver = requireDriver(id);
        Instant now = clock.instant();
        DriverAccountStatus before = driver.getAccountStatus();
        driver.updateAccountStatus(accountStatus, now);
        auditService.log("ADMIN", operatorId, "DRIVER", id.toString(), "DRIVER_STATUS_UPDATED",
                Map.of("accountStatus", before.name()),
                Map.of("accountStatus", driver.getAccountStatus().name(), "workStatus", driver.getWorkStatus().name()),
                null, requestId, now);
        return view(driver);
    }

    @Transactional(readOnly = true)
    public QrView qr(Long id) {
        DriverEntity driver = requireDriver(id);
        String path = "/ride/d/" + driver.getQrShortCode();
        return new QrView(driver.getId(), driver.getDriverNo(), driver.getQrShortCode(), path, qrCodeRenderer.dataUrl(path));
    }

    private DriverEntity requireDriver(Long id) {
        return driverRepository.findById(id)
                .orElseThrow(() -> new BusinessException("DRIVER_NOT_FOUND", "司机不存在"));
    }

    private DriverView view(DriverEntity driver) {
        VehicleEntity vehicle = driver.getDefaultVehicleId() == null
                ? null
                : vehicleRepository.findById(driver.getDefaultVehicleId()).orElse(null);
        return view(driver, vehicle);
    }

    private DriverView view(DriverEntity driver, VehicleEntity vehicle) {
        return DriverView.from(driver, vehicle);
    }

    private Map<String, Object> snapshot(DriverEntity driver, VehicleEntity vehicle) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("driverNo", driver.getDriverNo());
        snapshot.put("name", driver.getName());
        snapshot.put("mobile", driver.getMobile());
        snapshot.put("accountStatus", driver.getAccountStatus().name());
        snapshot.put("workStatus", driver.getWorkStatus().name());
        snapshot.put("maxPassengers", driver.getMaxPassengers());
        snapshot.put("availablePassengers", driver.getAvailablePassengers());
        snapshot.put("plateNo", vehicle == null ? null : vehicle.getPlateNo());
        snapshot.put("brandModel", vehicle == null ? null : vehicle.getBrandModel());
        return snapshot;
    }

    private String uniqueShortCode() {
        for (int attempt = 0; attempt < 5; attempt++) {
            String code = qrShortCodeService.generate();
            if (!driverRepository.existsByQrShortCode(code)) {
                return code;
            }
        }
        throw new IllegalStateException("Unable to allocate driver QR short code");
    }

    public record CreateDriverCommand(
            String driverNo,
            String name,
            String mobile,
            String password,
            int maxPassengers,
            int availablePassengers,
            String plateNo,
            String brandModel) {
    }

    public record UpdateDriverCommand(
            String name,
            String mobile,
            String password,
            int maxPassengers,
            int availablePassengers,
            String plateNo,
            String brandModel) {
    }

    public record QrView(Long driverId, String driverNo, String shortCode, String path, String imageDataUrl) {
    }

    public record DriverDetailView(
            DriverView driver,
            List<OrderSummary> activeOrders,
            List<OrderSummary> historyOrders,
            long completedOrderCount,
            long businessIncome,
            long availableBalance,
            List<WithdrawalSummary> withdrawals) {
    }

    public record OrderSummary(String orderNo, OrderStatus status, Instant createdAt, Long finalAmount) {
        static OrderSummary from(RideOrderEntity order) {
            return new OrderSummary(order.getOrderNo(), order.getStatus(), order.getCreatedAt(), order.getFinalAmount());
        }
    }

    public record WithdrawalSummary(
            String withdrawalNo,
            long amount,
            String channel,
            String account,
            String status,
            Instant createdAt) {
        static WithdrawalSummary from(WithdrawalEntity withdrawal) {
            return new WithdrawalSummary(
                    withdrawal.getWithdrawalNo(), withdrawal.getAmount(), withdrawal.getChannel(),
                    maskAccount(withdrawal.getAccount()), withdrawal.getStatus().name(), withdrawal.getCreatedAt());
        }

        private static String maskAccount(String account) {
            if (account == null || account.length() <= 4) return "****";
            return "****" + account.substring(account.length() - 4);
        }
    }

    public record DriverView(
            Long id,
            String driverNo,
            String name,
            String mobile,
            DriverAccountStatus accountStatus,
            DriverWorkStatus workStatus,
            int maxPassengers,
            int availablePassengers,
            String qrShortCode,
            Long vehicleId,
            String plateNo,
            String brandModel) {
        static DriverView from(DriverEntity driver, VehicleEntity vehicle) {
            return new DriverView(
                    driver.getId(), driver.getDriverNo(), driver.getName(), driver.getMobile(),
                    driver.getAccountStatus(), driver.getWorkStatus(), driver.getMaxPassengers(),
                    driver.getAvailablePassengers(), driver.getQrShortCode(),
                    vehicle == null ? null : vehicle.getId(),
                    vehicle == null ? null : vehicle.getPlateNo(),
                    vehicle == null ? null : vehicle.getBrandModel());
        }
    }
}
