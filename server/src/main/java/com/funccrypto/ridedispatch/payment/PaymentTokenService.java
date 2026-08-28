package com.funccrypto.ridedispatch.payment;

import org.springframework.stereotype.Service;

import com.funccrypto.ridedispatch.order.PassengerAccessTokenService;

/** Generates one-time payment tokens while keeping only their SHA-256 hash in storage. */
@Service
public class PaymentTokenService {

    private final PassengerAccessTokenService tokenService;

    public PaymentTokenService(PassengerAccessTokenService tokenService) {
        this.tokenService = tokenService;
    }

    public PassengerAccessTokenService.GeneratedToken generate() {
        return tokenService.generate();
    }

    public boolean matches(String rawToken, String expectedHash) {
        return tokenService.matches(rawToken, expectedHash);
    }

    public String hashOf(String rawToken) {
        return tokenService.hashOf(rawToken);
    }
}
