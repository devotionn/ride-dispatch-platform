package com.funccrypto.ridedispatch.order;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import com.funccrypto.ridedispatch.dispatch.DispatchAttemptEntity;
import com.funccrypto.ridedispatch.dispatch.DispatchAttemptRepository;
import com.funccrypto.ridedispatch.dispatch.DispatchAttemptStatus;
import com.funccrypto.ridedispatch.dispatch.DispatchType;
import com.funccrypto.ridedispatch.driver.DriverAccountStatus;
import com.funccrypto.ridedispatch.driver.DriverEntity;
import com.funccrypto.ridedispatch.driver.DriverRepository;
import com.funccrypto.ridedispatch.shared.error.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PublicOrderService {

    private final RideOrderRepository orderRepository;
    private final DriverRepository driverRepository;
    private final DispatchAttemptRepository dispatchAttemptRepository;
    private final PassengerOrderAccessTokenRepository passengerTokenRepository;
    private final PassengerAccessTokenService tokenService;
    private final OrderRequestFingerprintService fingerprintService;
    private final Clock clock;

    public PublicOrderService(
            RideOrderRepository orderRepository,
            DriverRepository driverRepository,
            DispatchAttemptRepository dispatchAttemptRepository,
            PassengerOrderAccessTokenRepository passengerTokenRepository,
            PassengerAccessTokenService tokenService,
            OrderRequestFingerprintService fingerprintService,
            Clock clock) {
        this.orderRepository = orderRepository;
        this.driverRepository = driverRepository;
        this.dispatchAttemptRepository = dispatchAttemptRepository;
        this.passengerTokenRepository = passengerTokenRepository;
        this.tokenService = tokenService;
        this.fingerprintService = fingerprintService;
        this.clock = clock;
    }

    @Transactional
    public CreateOrderResult create(CreateOrderCommand command) {
        return create(command, null);
    }

    @Transactional
    public CreateOrderResult create(CreateOrderCommand command, String rawIdempotencyKey) {
        if (command.sourceType() == OrderSourceType.ADMIN_CREATED) {
            throw new BusinessException("INVALID_ORDER_SOURCE", "公共接口不能创建后台代客订单");
        }

        String idempotencyKey = normalizeIdempotencyKey(rawIdempotencyKey);
        String requestFingerprint = idempotencyKey == null ? null : fingerprintService.fingerprint(command);
        Instant now = clock.instant();

        if (idempotencyKey != null) {
            RideOrderEntity existing = orderRepository.findByIdempotencyKey(idempotencyKey).orElse(null);
            if (existing != null) {
                return replay(existing, requestFingerprint, now);
            }
        }

        Long sourceDriverId = null;
        OrderStatus initialStatus = OrderStatus.PENDING_DISPATCH;
        DriverEntity sourceDriver = null;

        if (command.sourceType() == OrderSourceType.DRIVER_QR) {
            if (command.driverShortCode() == null || command.driverShortCode().isBlank()) {
                throw new BusinessException("DRIVER_SHORT_CODE_REQUIRED", "司机定向订单缺少司机二维码标识");
            }
            sourceDriver = driverRepository.findByQrShortCode(command.driverShortCode())
                    .orElseThrow(() -> new BusinessException("DRIVER_NOT_FOUND", "司机二维码已失效"));
            if (sourceDriver.getAccountStatus() != DriverAccountStatus.ACTIVE) {
                throw new BusinessException("DRIVER_DISABLED", "该司机当前不可接收定向订单");
            }
            sourceDriverId = sourceDriver.getId();
            initialStatus = OrderStatus.PENDING_DRIVER_CONFIRM;
        } else if (command.driverShortCode() != null && !command.driverShortCode().isBlank()) {
            throw new BusinessException("INVALID_DRIVER_BINDING", "公共订单不能绑定司机二维码");
        }

        PassengerAccessTokenService.GeneratedToken token = tokenService.generate();
        RideOrderEntity order = new RideOrderEntity(
                nextOrderNo(),
                command.sourceType(),
                sourceDriverId,
                command.passengerMobile(),
                token.hash(),
                idempotencyKey,
                requestFingerprint,
                command.pickupAddress(),
                command.pickupLatitude(),
                command.pickupLongitude(),
                command.destinationAddress(),
                command.destinationLatitude(),
                command.destinationLongitude(),
                command.passengerCount(),
                command.departureAt(),
                command.remark(),
                initialStatus,
                now);
        orderRepository.save(order);

        if (sourceDriver != null) {
            dispatchAttemptRepository.save(new DispatchAttemptEntity(
                    order.getId(),
                    sourceDriver.getId(),
                    DispatchType.DIRECT_QR,
                    null,
                    now));
        }

        return new CreateOrderResult(order.getOrderNo(), order.getStatus(), token.raw());
    }

    @Transactional(readOnly = true)
    public RideOrderEntity getForPassenger(String orderNo, String accessToken) {
        RideOrderEntity order = orderRepository.findByOrderNo(orderNo)
                .orElseThrow(() -> new BusinessException("ORDER_NOT_FOUND", "订单不存在"));
        verifyToken(order, accessToken);
        return order;
    }

    @Transactional
    public OrderStatus cancel(String orderNo, String accessToken) {
        RideOrderEntity order = orderRepository.findByOrderNoForUpdate(orderNo)
                .orElseThrow(() -> new BusinessException("ORDER_NOT_FOUND", "订单不存在"));
        verifyToken(order, accessToken);

        Instant now = clock.instant();
        if (order.getStatus() == OrderStatus.PENDING_DRIVER_CONFIRM) {
            dispatchAttemptRepository.findFirstByOrderIdAndStatusOrderByDispatchedAtDesc(
                            order.getId(), DispatchAttemptStatus.WAITING)
                    .ifPresent(attempt -> attempt.invalidateByOrder(now));
        }
        order.cancelBeforeAcceptance(now);
        return order.getStatus();
    }

    private CreateOrderResult replay(RideOrderEntity existing, String requestFingerprint, Instant now) {
        if (!requestFingerprint.equals(existing.getRequestFingerprint())) {
            throw new BusinessException("IDEMPOTENCY_CONFLICT", "同一幂等键不能用于不同的下单内容");
        }
        PassengerAccessTokenService.GeneratedToken replayToken = tokenService.generate();
        passengerTokenRepository.save(new PassengerOrderAccessTokenEntity(
                existing.getId(), replayToken.hash(), now));
        return new CreateOrderResult(existing.getOrderNo(), existing.getStatus(), replayToken.raw());
    }

    private void verifyToken(RideOrderEntity order, String accessToken) {
        boolean primaryToken = tokenService.matches(accessToken, order.getPassengerAccessTokenHash());
        boolean replayToken = !primaryToken
                && accessToken != null
                && !accessToken.isBlank()
                && passengerTokenRepository.existsByOrderIdAndTokenHash(order.getId(), tokenService.hashOf(accessToken));
        if (!primaryToken && !replayToken) {
            throw new BusinessException("ORDER_ACCESS_DENIED", "无权访问该订单");
        }
    }

    private String normalizeIdempotencyKey(String rawKey) {
        if (rawKey == null || rawKey.isBlank()) return null;
        String key = rawKey.trim();
        if (key.length() < 16 || key.length() > 80) {
            throw new BusinessException("IDEMPOTENCY_KEY_INVALID", "Idempotency-Key 长度必须为 16-80 个字符");
        }
        return key;
    }

    private String nextOrderNo() {
        return "RD" + UUID.randomUUID().toString().replace("-", "").substring(0, 20).toUpperCase();
    }

    public record CreateOrderCommand(
            OrderSourceType sourceType,
            String driverShortCode,
            String pickupAddress,
            java.math.BigDecimal pickupLatitude,
            java.math.BigDecimal pickupLongitude,
            String destinationAddress,
            java.math.BigDecimal destinationLatitude,
            java.math.BigDecimal destinationLongitude,
            int passengerCount,
            Instant departureAt,
            String passengerMobile,
            String remark) {
    }

    public record CreateOrderResult(String orderNo, OrderStatus status, String passengerAccessToken) {
    }
}
