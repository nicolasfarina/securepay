package com.securepay.security;

import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

@Component
public class TokenRevocationValidator implements OAuth2TokenValidator<Jwt> {
    private static final Logger log = LoggerFactory.getLogger(TokenRevocationValidator.class);
    private static final OAuth2Error INVALID_TOKEN =
            new OAuth2Error("invalid_token", "Token is invalid or revoked", null);
    private static final OAuth2Error REVOCATION_UNAVAILABLE =
            new OAuth2Error("invalid_token", "Token revocation check is unavailable", null);

    private final TokenRevocationService revocations;
    private final MeterRegistry meters;

    public TokenRevocationValidator(TokenRevocationService revocations, MeterRegistry meters) {
        this.revocations = revocations;
        this.meters = meters;
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt jwt) {
        String tokenId = jwt.getId();
        if (tokenId == null || tokenId.isBlank() || jwt.getExpiresAt() == null) {
            return OAuth2TokenValidatorResult.failure(INVALID_TOKEN);
        }
        try {
            if (revocations.isRevoked(tokenId)) {
                meters.counter("securepay.security.token_revocations", "result", "revoked").increment();
                return OAuth2TokenValidatorResult.failure(INVALID_TOKEN);
            }
            meters.counter("securepay.security.token_revocations", "result", "accepted").increment();
            return OAuth2TokenValidatorResult.success();
        } catch (DataAccessException exception) {
            meters.counter("securepay.security.token_revocation_store_errors").increment();
            log.warn("Token revocation store is unavailable; rejecting the bearer token", exception);
            return OAuth2TokenValidatorResult.failure(REVOCATION_UNAVAILABLE);
        }
    }
}
