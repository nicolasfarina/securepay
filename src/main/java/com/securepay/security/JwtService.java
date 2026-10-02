package com.securepay.security;

import com.securepay.auth.domain.UserAccount;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Date;

@Service
public class JwtService {
    private final SecretKey signingKey;
    private final long expirationSeconds;

    public JwtService(@Value("${securepay.security.jwt-secret}") String secret,
                      @Value("${securepay.security.jwt-expiration-seconds:3600}") long expirationSeconds) {
        byte[] keyBytes = secret.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        this.signingKey = Keys.hmacShaKeyFor(keyBytes);
        if (expirationSeconds <= 0) throw new IllegalArgumentException("JWT expiration must be positive");
        this.expirationSeconds = expirationSeconds;
    }

    public String issueToken(UserAccount account) {
        Instant now = Instant.now();
        return Jwts.builder().subject(account.username()).claim("role", account.role().name())
                .issuedAt(Date.from(now)).expiration(Date.from(now.plusSeconds(expirationSeconds)))
                .signWith(signingKey).compact();
    }

    public Claims parse(String token) {
        return Jwts.parser().verifyWith(signingKey).build().parseSignedClaims(token).getPayload();
    }
}
