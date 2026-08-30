package com.funccrypto.ridedispatch.dispatch;

import java.time.Instant;

public record NearbyDriverView(
        Long driverId,
        String driverNo,
        String driverName,
        int availablePassengers,
        Double straightLineDistanceKm,
        Instant locatedAt) {
}
