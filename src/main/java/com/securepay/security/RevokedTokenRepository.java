package com.securepay.security;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;

interface RevokedTokenRepository extends JpaRepository<RevokedTokenEntity, String> {
    long deleteByExpiresAtLessThanEqual(Instant now);
}
