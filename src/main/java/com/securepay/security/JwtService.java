package com.securepay.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class JwtService {

    private static final Base64.Encoder BASE64_URL_ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder BASE64_URL_DECODER = Base64.getUrlDecoder();
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final String HEADER = "{\"alg\":\"HS256\",\"typ\":\"JWT\"}";

    private final byte[] secret;
    private final long expirationSeconds;

    public JwtService(
            @Value("${securepay.security.jwt-secret}") String jwtSecret,
            @Value("${securepay.security.jwt-expiration-seconds:3600}") long expirationSeconds) {
        if (jwtSecret == null || jwtSecret.length() < 32) {
            throw new IllegalArgumentException("securepay.security.jwt-secret must contain at least 32 characters");
        }
        this.secret = jwtSecret.getBytes(StandardCharsets.UTF_8);
        this.expirationSeconds = expirationSeconds;
    }

    public String generateToken(String username, String role) {
        Instant now = Instant.now();
        String payload = "{"
                + "\"iss\":\"securepay\","
                + "\"sub\":\"" + jsonEscape(username) + "\","
                + "\"role\":\"" + jsonEscape(role) + "\","
                + "\"iat\":" + now.getEpochSecond() + ","
                + "\"exp\":" + now.plusSeconds(expirationSeconds).getEpochSecond()
                + "}";

        String encodedHeader = encode(HEADER);
        String encodedPayload = encode(payload);
        String signingInput = encodedHeader + "." + encodedPayload;
        String signature = BASE64_URL_ENCODER.encodeToString(sign(signingInput));

        return signingInput + "." + signature;
    }

    public JwtClaims parseAndValidate(String token) {
        String[] parts = token.split("\\.");
        if (parts.length != 3) {
            throw new IllegalArgumentException("invalid JWT");
        }

        String signingInput = parts[0] + "." + parts[1];
        byte[] providedSignature;
        try {
            providedSignature = BASE64_URL_DECODER.decode(parts[2]);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("invalid JWT signature", exception);
        }

        if (!MessageDigest.isEqual(providedSignature, sign(signingInput))) {
            throw new IllegalArgumentException("invalid JWT signature");
        }

        String header = decode(parts[0]);
        if (!HEADER.equals(header)) {
            throw new IllegalArgumentException("unsupported JWT header");
        }

        String payload = decode(parts[1]);
        String issuer = stringClaim(payload, "iss");
        String subject = stringClaim(payload, "sub");
        String role = stringClaim(payload, "role");
        long expiresAt = longClaim(payload, "exp");

        if (!"securepay".equals(issuer) || subject.isBlank() || role.isBlank()) {
            throw new IllegalArgumentException("invalid JWT claims");
        }
        if (Instant.now().getEpochSecond() >= expiresAt) {
            throw new IllegalArgumentException("expired JWT");
        }

        return new JwtClaims(subject, role, expiresAt);
    }

    public long expirationSeconds() {
        return expirationSeconds;
    }

    private String encode(String value) {
        return BASE64_URL_ENCODER.encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private String decode(String value) {
        try {
            return new String(BASE64_URL_DECODER.decode(value), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("invalid JWT encoding", exception);
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

    private String stringClaim(String json, String name) {
        Pattern pattern = Pattern.compile("\\\"" + Pattern.quote(name) + "\\\"\\s*:\\s*\\\"((?:\\\\.|[^\\\"])*)\\\"");
        Matcher matcher = pattern.matcher(json);
        if (!matcher.find()) {
            throw new IllegalArgumentException("missing JWT claim: " + name);
        }
        return jsonUnescape(matcher.group(1));
    }

    private long longClaim(String json, String name) {
        Pattern pattern = Pattern.compile("\\\"" + Pattern.quote(name) + "\\\"\\s*:\\s*(\\d+)");
        Matcher matcher = pattern.matcher(json);
        if (!matcher.find()) {
            throw new IllegalArgumentException("missing JWT claim: " + name);
        }
        return Long.parseLong(matcher.group(1));
    }

    private String jsonEscape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private String jsonUnescape(String value) {
        StringBuilder result = new StringBuilder();
        boolean escaped = false;
        for (char character : value.toCharArray()) {
            if (escaped) {
                result.append(character);
                escaped = false;
            } else if (character == '\\') {
                escaped = true;
            } else {
                result.append(character);
            }
        }
        if (escaped) {
            throw new IllegalArgumentException("invalid escaped JWT claim");
        }
        return result.toString();
    }

    public record JwtClaims(String subject, String role, long expiresAt) {
    }
}
