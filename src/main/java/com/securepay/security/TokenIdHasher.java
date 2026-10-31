package com.securepay.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

final class TokenIdHasher {
    private TokenIdHasher() {
    }

    static String sha256(String tokenId) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(tokenId.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
