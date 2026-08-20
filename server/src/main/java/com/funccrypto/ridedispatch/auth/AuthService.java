package com.funccrypto.ridedispatch.auth;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import com.funccrypto.ridedispatch.driver.DriverAccountStatus;
import com.funccrypto.ridedispatch.driver.DriverEntity;
import com.funccrypto.ridedispatch.driver.DriverRepository;
import com.funccrypto.ridedispatch.shared.error.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final AdminUserRepository adminRepository;
    private final DriverRepository driverRepository;
    private final AuthSessionRepository sessionRepository;
    private final OpaqueTokenService tokenService;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;
    private final Duration adminTtl;
    private final Duration driverTtl;

    public AuthService(
            AdminUserRepository adminRepository,
            DriverRepository driverRepository,
            AuthSessionRepository sessionRepository,
            OpaqueTokenService tokenService,
            PasswordEncoder passwordEncoder,
            Clock clock,
            @Value("${app.auth.admin-session-ttl:PT12H}") Duration adminTtl,
            @Value("${app.auth.driver-session-ttl:P7D}") Duration driverTtl) {
        this.adminRepository = adminRepository;
        this.driverRepository = driverRepository;
        this.sessionRepository = sessionRepository;
        this.tokenService = tokenService;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
        this.adminTtl = adminTtl;
        this.driverTtl = driverTtl;
    }

    @Transactional
    public LoginResult loginAdmin(String username, String password) {
        AdminUserEntity admin = adminRepository.findByUsername(username)
                .orElseThrow(this::invalidCredentials);
        if (admin.getStatus() != AdminAccountStatus.ACTIVE
                || !passwordEncoder.matches(password, admin.getPasswordHash())) {
            throw invalidCredentials();
        }
        Instant now = clock.instant();
        admin.markLogin(now);
        return issue(
                AuthPrincipalType.ADMIN,
                admin.getId(),
                "ROLE_" + admin.getRole().name(),
                now,
                adminTtl);
    }

    @Transactional
    public LoginResult loginDriver(String driverNo, String password) {
        DriverEntity driver = driverRepository.findByDriverNo(driverNo)
                .orElseThrow(this::invalidCredentials);
        if (driver.getAccountStatus() != DriverAccountStatus.ACTIVE
                || driver.getPasswordHash() == null
                || !passwordEncoder.matches(password, driver.getPasswordHash())) {
            throw invalidCredentials();
        }
        Instant now = clock.instant();
        return issue(AuthPrincipalType.DRIVER, driver.getId(), "ROLE_DRIVER", now, driverTtl);
    }

    @Transactional(readOnly = true)
    public Optional<AuthenticatedPrincipal> authenticate(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return Optional.empty();
        }
        Instant now = clock.instant();
        return sessionRepository.findByTokenHashAndRevokedAtIsNull(tokenService.hash(rawToken))
                .filter(session -> session.getExpiresAt().isAfter(now))
                .flatMap(this::resolveActivePrincipal);
    }

    @Transactional
    public void revoke(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return;
        }
        sessionRepository.findByTokenHashAndRevokedAtIsNull(tokenService.hash(rawToken))
                .ifPresent(session -> session.revoke(clock.instant()));
    }

    private Optional<AuthenticatedPrincipal> resolveActivePrincipal(AuthSessionEntity session) {
        if (session.getPrincipalType() == AuthPrincipalType.ADMIN) {
            return adminRepository.findById(session.getPrincipalId())
                    .filter(admin -> admin.getStatus() == AdminAccountStatus.ACTIVE)
                    .map(admin -> new AuthenticatedPrincipal(
                            AuthPrincipalType.ADMIN,
                            admin.getId(),
                            "ROLE_" + admin.getRole().name(),
                            admin.getDisplayName()));
        }
        return driverRepository.findById(session.getPrincipalId())
                .filter(driver -> driver.getAccountStatus() == DriverAccountStatus.ACTIVE)
                .map(driver -> new AuthenticatedPrincipal(
                        AuthPrincipalType.DRIVER,
                        driver.getId(),
                        "ROLE_DRIVER",
                        driver.getName()));
    }

    private LoginResult issue(
            AuthPrincipalType principalType,
            Long principalId,
            String authority,
            Instant now,
            Duration ttl) {
        OpaqueTokenService.GeneratedToken token = tokenService.generate();
        Instant expiresAt = now.plus(ttl);
        sessionRepository.save(new AuthSessionEntity(
                token.hash(), principalType, principalId, authority, now, expiresAt));
        return new LoginResult(token.raw(), expiresAt, authority);
    }

    private BusinessException invalidCredentials() {
        return new BusinessException("AUTH_INVALID_CREDENTIALS", "账号或密码错误");
    }

    public record LoginResult(String accessToken, Instant expiresAt, String authority) {
    }
}
