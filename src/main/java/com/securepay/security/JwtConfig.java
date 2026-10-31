package com.securepay.security;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.scheduling.annotation.EnableScheduling;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

@Configuration
@EnableScheduling
public class JwtConfig {
    @Bean
    SecretKey jwtSigningKey(@Value("${securepay.security.jwt-secret}") String secret) {
        if (secret == null || secret.length() < 32) {
            throw new IllegalArgumentException("securepay.security.jwt-secret must contain at least 32 characters");
        }
        return new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }

    @Bean
    JwtEncoder jwtEncoder(SecretKey jwtSigningKey) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(jwtSigningKey));
    }

    @Bean
    JwtDecoder jwtDecoder(SecretKey jwtSigningKey, OAuth2TokenValidator<Jwt> revocationValidator) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(jwtSigningKey)
                .macAlgorithm(MacAlgorithm.HS256).build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                new JwtTimestampValidator(Duration.ZERO),
                new JwtIssuerValidator("securepay"),
                revocationValidator,
                jwt -> {
                    Object subject = jwt.getClaims().get("sub");
                    Object role = jwt.getClaims().get("role");
                    if (subject instanceof String name && !name.isBlank()
                            && role instanceof String authority && !authority.isBlank()
                            && jwt.getExpiresAt() != null) {
                        return OAuth2TokenValidatorResult.success();
                    }
                    return OAuth2TokenValidatorResult.failure(
                            new OAuth2Error("invalid_token", "Required JWT claims are missing or invalid", null));
                }));
        return decoder;
    }
}

