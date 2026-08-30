package com.funccrypto.ridedispatch.auth;

import java.time.Clock;
import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminBootstrapInitializerTest {

    private final AdminUserRepository repository = Mockito.mock(AdminUserRepository.class);
    private final PasswordEncoder passwordEncoder = Mockito.mock(PasswordEncoder.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-08-30T00:00:00Z"), Clock.systemUTC().getZone());

    @Test
    void skipsBootstrapWhenNoCredentialsConfigured() {
        AdminBootstrapInitializer initializer = initializer("", "", new MockEnvironment());

        assertThatCode(() -> initializer.run(null)).doesNotThrowAnyException();
        verify(repository, never()).count();
    }

    @Test
    void rejectsPartiallyConfiguredCredentials() {
        AdminBootstrapInitializer initializer = initializer("admin", "", new MockEnvironment());

        assertThatThrownBy(() -> initializer.run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Both bootstrap admin username and password");
    }

    @Test
    void createsAdminWhenRepositoryEmpty() {
        when(repository.count()).thenReturn(0L);
        when(passwordEncoder.encode("a-strong-passphrase")).thenReturn("{bcrypt}hash");
        MockEnvironment environment = new MockEnvironment();

        AdminBootstrapInitializer initializer = initializer("admin", "a-strong-passphrase", environment);

        assertThatCode(() -> initializer.run(null)).doesNotThrowAnyException();
        verify(repository).save(any(AdminUserEntity.class));
    }

    @Test
    void keepsExistingAdminsWhenRepositoryNotEmpty() {
        when(repository.count()).thenReturn(1L);
        MockEnvironment environment = new MockEnvironment();

        AdminBootstrapInitializer initializer = initializer("admin", "a-strong-passphrase", environment);

        assertThatCode(() -> initializer.run(null)).doesNotThrowAnyException();
        verify(repository, never()).save(any(AdminUserEntity.class));
    }

    @Test
    void blocksKnownDefaultPasswordInProductionProfile() {
        when(repository.count()).thenReturn(0L);
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("production");

        AdminBootstrapInitializer initializer = initializer("admin", "admin123", environment);

        assertThatThrownBy(() -> initializer.run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("known default password");
        verify(repository, never()).save(any(AdminUserEntity.class));
    }

    @Test
    void allowsAnyPasswordOutsideProductionProfile() {
        when(repository.count()).thenReturn(0L);
        when(passwordEncoder.encode("admin123")).thenReturn("{bcrypt}hash");
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("local");

        AdminBootstrapInitializer initializer = initializer("admin", "admin123", environment);

        assertThatCode(() -> initializer.run(null)).doesNotThrowAnyException();
        verify(repository).save(any(AdminUserEntity.class));
    }

    private AdminBootstrapInitializer initializer(String username, String password, MockEnvironment environment) {
        return new AdminBootstrapInitializer(
                repository,
                passwordEncoder,
                clock,
                environment,
                username,
                password,
                "System Admin");
    }
}
