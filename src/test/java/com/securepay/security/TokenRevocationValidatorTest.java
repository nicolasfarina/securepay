package com.securepay.security;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TokenRevocationValidatorTest {
    private final TokenRevocationService revocations = mock(TokenRevocationService.class);
    private final SimpleMeterRegistry meters = new SimpleMeterRegistry();
    private final TokenRevocationValidator validator = new TokenRevocationValidator(revocations, meters);

    @Test
    void acceptsAnActiveTokenAndCountsTheResult() {
        when(revocations.isRevoked("token-1")).thenReturn(false);
        assertThat(validator.validate(token("token-1")).hasErrors()).isFalse();
        assertThat(meters.get("securepay.security.token_revocations").tag("result", "accepted")
                .counter().count()).isEqualTo(1);
    }

    @Test
    void rejectsRevokedTokensAndCountsTheResult() {
        when(revocations.isRevoked("token-2")).thenReturn(true);
        OAuth2TokenValidatorResult result = validator.validate(token("token-2"));
        assertThat(result.hasErrors()).isTrue();
        assertThat(result.getErrors()).anySatisfy(error -> assertThat(error.getDescription())
                .contains("revoked"));
        assertThat(meters.get("securepay.security.token_revocations").tag("result", "revoked")
                .counter().count()).isEqualTo(1);
    }

    @Test
    void failsClosedAndCountsUnavailableStoreWithoutLeakingToken() {
        when(revocations.isRevoked("sensitive-token-id"))
                .thenThrow(new DataAccessResourceFailureException("database unavailable"));
        OAuth2TokenValidatorResult result = validator.validate(token("sensitive-token-id"));
        assertThat(result.hasErrors()).isTrue();
        assertThat(result.getErrors()).anySatisfy(error ->
                assertThat(error.getDescription()).contains("unavailable").doesNotContain("sensitive-token-id"));
        assertThat(meters.counter("securepay.security.token_revocation_store_errors").count()).isEqualTo(1);
    }

    @Test
    void rejectsTokensWithoutAnIdOrExpiry() {
        assertThat(validator.validate(Jwt.withTokenValue("test").header("alg", "HS256")
                .subject("customer-123").build()).hasErrors()).isTrue();
    }

    private static Jwt token(String tokenId) {
        return Jwt.withTokenValue("test").header("alg", "HS256").claim("jti", tokenId)
                .subject("customer-123").expiresAt(Instant.now().plusSeconds(60)).build();
    }
}
