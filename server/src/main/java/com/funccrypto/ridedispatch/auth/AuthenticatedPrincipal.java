package com.funccrypto.ridedispatch.auth;

public record AuthenticatedPrincipal(
        AuthPrincipalType principalType,
        Long principalId,
        String authority,
        String displayName) {
}
