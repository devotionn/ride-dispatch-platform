package com.funccrypto.ridedispatch.auth.api;

import com.funccrypto.ridedispatch.auth.AuthService;
import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/admin/login")
    LoginResponse adminLogin(@Valid @RequestBody LoginRequest request) {
        AuthService.LoginResult result = authService.loginAdmin(request.username(), request.password());
        return new LoginResponse(result.accessToken(), result.expiresAt(), result.authority());
    }

    @PostMapping("/driver/login")
    LoginResponse driverLogin(@Valid @RequestBody LoginRequest request) {
        AuthService.LoginResult result = authService.loginDriver(request.username(), request.password());
        return new LoginResponse(result.accessToken(), result.expiresAt(), result.authority());
    }

    @PostMapping("/logout")
    void logout(@RequestHeader("Authorization") String authorization) {
        String prefix = "Bearer ";
        if (authorization != null && authorization.startsWith(prefix)) {
            authService.revoke(authorization.substring(prefix.length()).trim());
        }
    }
}
