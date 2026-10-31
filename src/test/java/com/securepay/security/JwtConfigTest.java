package com.securepay.security;

import com.securepay.support.TestJwtTokens;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtConfigTest {
    private static final String SECRET = "test-only-key-with-at-least-sixty-four-bytes-for-hs512-and-hs256-tests";
    private final JwtConfig config = new JwtConfig();

    @Test
    void onlyHs256IsAcceptedEvenWhenHs512HasAValidSignatureWithTheSameKey() {
        var decoder = config.jwtDecoder(config.jwtSigningKey(SECRET), jwt -> OAuth2TokenValidatorResult.success());
        String valid = TestJwtTokens.signed(SECRET, "customer-123", "CUSTOMER", 60);
        assertThat(decoder.decode(valid).getSubject()).isEqualTo("customer-123");
        String hs512 = TestJwtTokens.signed(SECRET, MacAlgorithm.HS512, b -> {});
        assertThatThrownBy(() -> decoder.decode(hs512)).isInstanceOf(JwtException.class);
    }

    @Test
    void standardEncoderPreservesEscapedAndUnicodeClaims() {
        var key = config.jwtSigningKey(SECRET);
        var service = new JwtService(config.jwtEncoder(key), 60);
        String subject = "Nicolás-\"quoted\"-\\-用户";
        var jwt = config.jwtDecoder(key, token -> OAuth2TokenValidatorResult.success())
                .decode(service.generateToken(subject, "CUSTOMER"));
        assertThat(jwt.getSubject()).isEqualTo(subject);
        assertThat(jwt.getClaimAsString("role")).isEqualTo("CUSTOMER");
        assertThat(service.expirationSeconds()).isEqualTo(60);
        assertThat(jwt.getExpiresAt()).isEqualTo(jwt.getIssuedAt().plusSeconds(60));
    }

    @Test
    void weakSigningKeyFailsAtStartup() {
        assertThatThrownBy(() -> config.jwtSigningKey("too-short"))
                .isInstanceOf(IllegalArgumentException.class);
    }

}
