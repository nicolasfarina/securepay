package com.securepay.security;

public record LoginResponse(
        String accessToken,
        String tokenType,
        long expiresIn) {
}
