package com.funccrypto.ridedispatch.auth;

import java.time.Clock;
import java.util.Arrays;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class AdminBootstrapInitializer implements ApplicationRunner {

    // Credentials that must never seed a production admin account, even by accident.
    private static final Set<String> BLOCKED_PRODUCTION_PASSWORDS = Set.of(
            "admin",
            "admin123",
            "admin888",
            "123456",
            "12345678",
            "123456789",
            "password",
            "root");

    private final AdminUserRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;
    private final Environment environment;
    private final String username;
    private final String password;
    private final String displayName;

    public AdminBootstrapInitializer(
            AdminUserRepository repository,
            PasswordEncoder passwordEncoder,
            Clock clock,
            Environment environment,
            @Value("${app.bootstrap-admin.username:}") String username,
            @Value("${app.bootstrap-admin.password:}") String password,
            @Value("${app.bootstrap-admin.display-name:System Admin}") String displayName) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
        this.environment = environment;
        this.username = username;
        this.password = password;
        this.displayName = displayName;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (username.isBlank() && password.isBlank()) {
            return;
        }
        if (username.isBlank() || password.isBlank()) {
            throw new IllegalStateException("Both bootstrap admin username and password must be configured");
        }
        if (isProductionProfile() && BLOCKED_PRODUCTION_PASSWORDS.contains(password)) {
            throw new IllegalStateException(
                    "Refusing to bootstrap a production admin with a known default password; "
                            + "set ADMIN_BOOTSTRAP_PASSWORD to a strong unique value");
        }
        if (repository.count() == 0) {
            repository.save(new AdminUserEntity(
                    username,
                    passwordEncoder.encode(password),
                    displayName,
                    AdminRole.ADMIN,
                    clock.instant()));
        }
    }

    private boolean isProductionProfile() {
        return Arrays.asList(environment.getActiveProfiles()).contains("production");
    }
}
