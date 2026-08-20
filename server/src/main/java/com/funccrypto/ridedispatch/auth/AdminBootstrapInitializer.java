package com.funccrypto.ridedispatch.auth;

import java.time.Clock;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class AdminBootstrapInitializer implements ApplicationRunner {

    private final AdminUserRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;
    private final String username;
    private final String password;
    private final String displayName;

    public AdminBootstrapInitializer(
            AdminUserRepository repository,
            PasswordEncoder passwordEncoder,
            Clock clock,
            @Value("${app.bootstrap-admin.username:}") String username,
            @Value("${app.bootstrap-admin.password:}") String password,
            @Value("${app.bootstrap-admin.display-name:System Admin}") String displayName) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
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
        if (repository.count() == 0) {
            repository.save(new AdminUserEntity(
                    username,
                    passwordEncoder.encode(password),
                    displayName,
                    AdminRole.ADMIN,
                    clock.instant()));
        }
    }
}
