package com.funccrypto.ridedispatch;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;

import com.funccrypto.ridedispatch.auth.AdminRole;
import com.funccrypto.ridedispatch.auth.AdminUserEntity;
import com.funccrypto.ridedispatch.auth.AdminUserRepository;
import com.funccrypto.ridedispatch.brand.PlatformBrandEntity;
import com.funccrypto.ridedispatch.brand.PlatformBrandRepository;
import com.funccrypto.ridedispatch.driver.DriverEntity;
import com.funccrypto.ridedispatch.driver.DriverLocationCurrentEntity;
import com.funccrypto.ridedispatch.driver.DriverLocationCurrentRepository;
import com.funccrypto.ridedispatch.driver.DriverLocationSource;
import com.funccrypto.ridedispatch.driver.DriverRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@Profile("local")
public class LocalDevSeeder implements CommandLineRunner {

    private final AdminUserRepository adminUserRepository;
    private final DriverRepository driverRepository;
    private final DriverLocationCurrentRepository locationRepository;
    private final PlatformBrandRepository brandRepository;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    public LocalDevSeeder(
            AdminUserRepository adminUserRepository,
            DriverRepository driverRepository,
            DriverLocationCurrentRepository locationRepository,
            PlatformBrandRepository brandRepository,
            PasswordEncoder passwordEncoder,
            Clock clock) {
        this.adminUserRepository = adminUserRepository;
        this.driverRepository = driverRepository;
        this.locationRepository = locationRepository;
        this.brandRepository = brandRepository;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
    }

    @Override
    public void run(String... args) {
        Instant now = clock.instant();
        if (brandRepository.findFirstByOrderByIdAsc().isEmpty()) {
            brandRepository.save(new PlatformBrandEntity("约车调度演示车队", null, null, now));
        }
        if (!adminUserRepository.existsByUsername("admin")) {
            adminUserRepository.save(new AdminUserEntity(
                    "admin", passwordEncoder.encode("admin123"), "管理员", AdminRole.ADMIN, now));
        }
        if (!adminUserRepository.existsByUsername("dispatcher")) {
            adminUserRepository.save(new AdminUserEntity(
                    "dispatcher", passwordEncoder.encode("dispatcher123"), "调度员", AdminRole.DISPATCHER, now));
        }
        if (!adminUserRepository.existsByUsername("finance")) {
            adminUserRepository.save(new AdminUserEntity(
                    "finance", passwordEncoder.encode("finance123"), "财务人员", AdminRole.FINANCE, now));
        }
        seedDriver("D101", "李师傅", "13800000101", "QRD101",
                new BigDecimal("32.3920000"), new BigDecimal("119.5070000"), now);
        seedDriver("D102", "王师傅", "13800000102", "QRD102",
                new BigDecimal("32.3900000"), new BigDecimal("119.5090000"), now);
    }

    private void seedDriver(String driverNo, String name, String mobile, String qr,
            BigDecimal latitude, BigDecimal longitude, Instant now) {
        if (driverRepository.existsByDriverNo(driverNo)) {
            return;
        }
        DriverEntity driver = driverRepository.save(DriverEntity.createWithPassword(
                driverNo, name, mobile, passwordEncoder.encode("driver123"),
                4, 4, qr, now));
        locationRepository.save(new DriverLocationCurrentEntity(
                driver.getId(), latitude, longitude, new BigDecimal("10.0"),
                DriverLocationSource.DRIVER_APP, now, now));
    }
}
