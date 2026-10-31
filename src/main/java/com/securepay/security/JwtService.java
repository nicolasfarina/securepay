package com.securepay.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
public class JwtService {
    private final JwtEncoder encoder;
    private final long expirationSeconds;

    public JwtService(JwtEncoder encoder,
            @Value("${securepay.security.jwt-expiration-seconds:900}") long expirationSeconds) {
        this.encoder = encoder;
        this.expirationSeconds = expirationSeconds;
    }

    public String generateToken(String username, String role) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("securepay").subject(username).claim("role", role).id(UUID.randomUUID().toString())
                .issuedAt(now).expiresAt(now.plusSeconds(expirationSeconds)).build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).type("JWT").build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    public long expirationSeconds() {
        return expirationSeconds;
    }
}
