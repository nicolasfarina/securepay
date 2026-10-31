package com.securepay.security;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@Transactional
public class TokenRevocationService {
    private final RevokedTokenRepository repository;

    public TokenRevocationService(RevokedTokenRepository repository) {
        this.repository = repository;
    }

    public boolean isRevoked(String tokenId) {
        return repository.existsById(TokenIdHasher.sha256(tokenId));
    }

    public void revoke(String tokenId, Instant expiresAt) {
        if (tokenId == null || tokenId.isBlank() || expiresAt == null) {
            throw new IllegalArgumentException("Token id and expiration are required");
        }
        String hash = TokenIdHasher.sha256(tokenId);
        if (!repository.existsById(hash)) {
            repository.save(new RevokedTokenEntity(hash, expiresAt));
        }
    }

    @Scheduled(fixedDelay = 3_600_000)
    public void removeExpiredRevocations() {
        repository.deleteByExpiresAtLessThanEqual(Instant.now());
    }
}
