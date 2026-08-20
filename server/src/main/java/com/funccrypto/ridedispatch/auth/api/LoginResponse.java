package com.funccrypto.ridedispatch.auth.api;

import java.time.Instant;

public record LoginResponse(String accessToken, Instant expiresAt, String authority) {
}
