package com.funccrypto.ridedispatch.dispatch;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.funccrypto.ridedispatch.driver.DriverAccountStatus;
import com.funccrypto.ridedispatch.driver.DriverEntity;
import com.funccrypto.ridedispatch.driver.DriverLocationCurrentEntity;
import com.funccrypto.ridedispatch.driver.DriverLocationCurrentRepository;
import com.funccrypto.ridedispatch.driver.DriverRepository;
import com.funccrypto.ridedispatch.driver.DriverWorkStatus;
import com.funccrypto.ridedispatch.order.RideOrderEntity;
import com.funccrypto.ridedispatch.order.RideOrderRepository;
import com.funccrypto.ridedispatch.shared.error.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NearbyDriverService {

    private static final Duration LOCATION_TTL = Duration.ofMinutes(5);
    private static final double RADIUS_KM = 10.0;
    private static final double EARTH_RADIUS_KM = 6371.0088;

    private final RideOrderRepository orderRepository;
    private final DriverRepository driverRepository;
    private final DriverLocationCurrentRepository locationRepository;
    private final Clock clock;

    public NearbyDriverService(
            RideOrderRepository orderRepository,
            DriverRepository driverRepository,
            DriverLocationCurrentRepository locationRepository,
            Clock clock) {
        this.orderRepository = orderRepository;
        this.driverRepository = driverRepository;
        this.locationRepository = locationRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<NearbyDriverView> findNearby(String orderNo) {
        RideOrderEntity order = orderRepository.findByOrderNo(orderNo)
                .orElseThrow(() -> new BusinessException("ORDER_NOT_FOUND", "订单不存在"));
        if (order.getPickupLatitude() == null || order.getPickupLongitude() == null) {
            throw new BusinessException(
                    "PICKUP_LOCATION_UNRESOLVED",
                    "上车点暂无定位坐标，无法计算附近司机，请人工选择司机");
        }

        List<DriverEntity> candidates = driverRepository
                .findByAccountStatusAndWorkStatusAndAvailablePassengersGreaterThanEqual(
                        DriverAccountStatus.ACTIVE,
                        DriverWorkStatus.AVAILABLE,
                        order.getPassengerCount());

        Map<Long, DriverLocationCurrentEntity> locations = locationRepository
                .findAllById(candidates.stream().map(DriverEntity::getId).toList())
                .stream()
                .collect(Collectors.toMap(DriverLocationCurrentEntity::getDriverId, Function.identity()));

        Instant cutoff = clock.instant().minus(LOCATION_TTL);
        return candidates.stream()
                .map(driver -> toView(driver, locations.get(driver.getId()), order, cutoff))
                .filter(java.util.Objects::nonNull)
                .sorted(Comparator.comparingDouble(NearbyDriverView::straightLineDistanceKm))
                .toList();
    }

    private NearbyDriverView toView(
            DriverEntity driver,
            DriverLocationCurrentEntity location,
            RideOrderEntity order,
            Instant cutoff) {
        if (location == null || location.getLocatedAt().isBefore(cutoff)) {
            return null;
        }
        double distance = haversineKm(
                order.getPickupLatitude(),
                order.getPickupLongitude(),
                location.getLatitude(),
                location.getLongitude());
        if (distance > RADIUS_KM) {
            return null;
        }
        return new NearbyDriverView(
                driver.getId(),
                driver.getDriverNo(),
                driver.getName(),
                driver.getAvailablePassengers(),
                Math.round(distance * 100.0) / 100.0,
                location.getLocatedAt());
    }

    static double haversineKm(BigDecimal lat1, BigDecimal lon1, BigDecimal lat2, BigDecimal lon2) {
        double phi1 = Math.toRadians(lat1.doubleValue());
        double phi2 = Math.toRadians(lat2.doubleValue());
        double deltaPhi = Math.toRadians(lat2.doubleValue() - lat1.doubleValue());
        double deltaLambda = Math.toRadians(lon2.doubleValue() - lon1.doubleValue());
        double a = Math.sin(deltaPhi / 2) * Math.sin(deltaPhi / 2)
                + Math.cos(phi1) * Math.cos(phi2)
                * Math.sin(deltaLambda / 2) * Math.sin(deltaLambda / 2);
        return EARTH_RADIUS_KM * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }
}
