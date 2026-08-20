package com.funccrypto.ridedispatch.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;

import com.funccrypto.ridedispatch.driver.DriverRepository;
import com.funccrypto.ridedispatch.shared.error.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class AuthServiceIntegrationTest {

    @Autowired
    AuthService authService;

    @Autowired
    AdminUserRepository adminRepository;

    @Autowired
    AuthSessionRepository sessionRepository;

    @Autowired
    DriverRepository driverRepository;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Autowired
    Clock clock;

    @BeforeEach
    void clean() {
        sessionRepository.deleteAll();
        adminRepository.deleteAll();
    }

    @Test
    void adminLoginStoresOnlyTokenHashAndCanBeRevoked() {
        adminRepository.save(new AdminUserEntity(
                "admin",
                passwordEncoder.encode("correct-horse-battery-staple"),
                "系统管理员",
                AdminRole.ADMIN,
                clock.instant()));

        AuthService.LoginResult login = authService.loginAdmin("admin", "correct-horse-battery-staple");
        AuthSessionEntity session = sessionRepository.findAll().getFirst();

        assertThat(login.accessToken()).isNotBlank();
        assertThat(session.getTokenHash()).doesNotContain(login.accessToken());
        assertThat(authService.authenticate(login.accessToken()))
                .get()
                .extracting(AuthenticatedPrincipal::authority)
                .isEqualTo("ROLE_ADMIN");

        authService.revoke(login.accessToken());
        assertThat(authService.authenticate(login.accessToken())).isEmpty();
    }

    @Test
    void wrongPasswordIsRejected() {
        adminRepository.save(new AdminUserEntity(
                "admin",
                passwordEncoder.encode("right-password"),
                "系统管理员",
                AdminRole.ADMIN,
                clock.instant()));

        assertThatThrownBy(() -> authService.loginAdmin("admin", "wrong-password"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("账号或密码错误");
    }
}
