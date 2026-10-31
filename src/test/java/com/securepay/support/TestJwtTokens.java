package com.securepay.support;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.function.Consumer;

public final class TestJwtTokens {
    private TestJwtTokens() {}

    public static String signed(String secret, String subject, String role, long expiresIn) {
        return signed(secret, MacAlgorithm.HS256, claims -> claims
                .subject(subject).claim("role", role)
                .issuedAt(Instant.now().minusSeconds(120))
                .expiresAt(Instant.now().plusSeconds(expiresIn)));
    }

    public static String signed(String secret, MacAlgorithm algorithm, Consumer<JwtClaimsSet.Builder> customize) {
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder().issuer("securepay")
                .subject("customer-123").claim("role", "CUSTOMER")
                .issuedAt(Instant.now().minusSeconds(120)).expiresAt(Instant.now().plusSeconds(60));
        customize.accept(claims);
        JwtEncoder encoder = new NimbusJwtEncoder(new ImmutableSecret<>(secret.getBytes(StandardCharsets.UTF_8)));
        return encoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(algorithm).type("JWT").build(), claims.build())).getTokenValue();
    }
}
