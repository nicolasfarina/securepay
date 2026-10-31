package com.securepay.security;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "revoked_tokens")
class RevokedTokenEntity {
    @Id
    @Column(name = "token_id_hash", nullable = false, length = 64)
    private String tokenIdHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at", nullable = false)
    private Instant revokedAt;

    protected RevokedTokenEntity() {
    }

    RevokedTokenEntity(String tokenIdHash, Instant expiresAt) {
        this.tokenIdHash = tokenIdHash;
        this.expiresAt = expiresAt;
        this.revokedAt = Instant.now();
    }

    Instant expiresAt() {
        return expiresAt;
    }

    String tokenIdHash() {
        return tokenIdHash;
    }
}
