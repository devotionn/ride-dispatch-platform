package com.funccrypto.ridedispatch.shared.error;

public record ApiError(String code, String message, String requestId) {
}
