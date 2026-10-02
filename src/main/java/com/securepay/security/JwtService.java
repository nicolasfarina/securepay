package com.securepay.security;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class JwtService {

    private static final Base64.Encoder BASE64_URL_ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder BASE64_URL_DECODER = Base64.getUrlDecoder();
    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final ObjectMapper objectMapper;
    private final byte[] secret;
    private final long expirationSeconds;

    public JwtService(
            ObjectMapper objectMapper,
            @Value("${securepay.security.jwt-secret}") String jwtSecret,
            @Value("${securepay.security.jwt-expiration-seconds:3600}") long expirationSeconds) {
        if (jwtSecret == null || jwtSecret.length() < 32) {
            throw new IllegalArgumentException("securepay.security.jwt-secret must contain at least 32 characters");
        }
        this.objectMapper = objectMapper;
        this.secret = jwtSecret.getBytes(StandardCharsets.UTF_8);
        this.expirationSeconds = expirationSeconds;
    }

    public String generateToken(String username, String role) {
        Instant now = Instant.now();

        Map<String, Object> header = Map.of(
                "alg", "HS256",
                "typ", "JWT");

        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("iss", "securepay");
        claims.put("sub", username);
        claims.put("role", role);
        claims.put("iat", now.getEpochSecond());
        claims.put("exp", now.plusSeconds(expirationSeconds).getEpochSecond());

        String encodedHeader = encodeJson(header);
        String encodedClaims = encodeJson(claims);
        String signingInput = encodedHeader + "." + encodedClaims;
        String signature = BASE64_URL_ENCODER.encodeToString(sign(signingInput));

        return signingInput + "." + signature;
    }

    public JwtClaims parseAndValidate(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length != 3) {
                throw new IllegalArgumentException("invalid JWT");
            }

            String signingInput = parts[0] + "." + parts[1];
            byte[] providedSignature = BASE64_URL_DECODER.decode(parts[2]);
            byte[] expectedSignature = sign(signingInput);

            if (!java.security.MessageDigest.isEqual(providedSignature, expectedSignature)) {
                throw new IllegalArgumentException("invalid JWT signature");
            }

            Map<String, Object> claims = objectMapper.readValue(
                    BASE64_URL_DECODER.decode(parts[1]),
                    new TypeReference<>() {});

            String issuer = String.valueOf(claims.get("iss"));
            String subject = String.valueOf(claims.get("sub"));
            String role = String.valueOf(claims.get("role"));
            long expiresAt = ((Number) claims.get("exp")).longValue();

            if (!"securepay".equals(issuer) || subject.isBlank() || role.isBlank()) {
                throw new IllegalArgumentException("invalid JWT claims");
            }
            if (Instant.now().getEpochSecond() >= expiresAt) {
                throw new IllegalArgumentException("expired JWT");
            }

            return new JwtClaims(subject, role, expiresAt);
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalArgumentException("invalid JWT", exception);
        }
    }

    private String encodeJson(Map<String, Object> value) {
        try {
            return BASE64_URL_ENCODER.encodeToString(objectMapper.writeValueAsBytes(value));
        } catch (Exception exception) {
            throw new IllegalStateException("could not encode JWT", exception);
        }
    }

    private byte[] sign(String value) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret, HMAC_ALGORITHM));
            return mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
        } catch (Exception exception) {
            throw new IllegalStateException("could not sign JWT", exception);
        }
    }

    public record JwtClaims(String subject, String role, long expiresAt) {
    }
}
