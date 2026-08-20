package com.funccrypto.ridedispatch.auth;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "auth_session")
public class AuthSessionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "principal_type", nullable = false, length = 20)
    private AuthPrincipalType principalType;

    @Column(name = "principal_id", nullable = false)
    private Long principalId;

    @Column(nullable = false, length = 40)
    private String authority;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    protected AuthSessionEntity() {
    }

    public AuthSessionEntity(
            String tokenHash,
            AuthPrincipalType principalType,
            Long principalId,
            String authority,
            Instant createdAt,
            Instant expiresAt) {
        this.tokenHash = tokenHash;
        this.principalType = principalType;
        this.principalId = principalId;
        this.authority = authority;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    public void revoke(Instant now) {
        if (revokedAt == null) {
            revokedAt = now;
        }
    }

    public Long getId() {
        return id;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public AuthPrincipalType getPrincipalType() {
        return principalType;
    }

    public Long getPrincipalId() {
        return principalId;
    }

    public String getAuthority() {
        return authority;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }
}
