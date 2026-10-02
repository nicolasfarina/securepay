package com.securepay.auth.api;

import com.securepay.auth.domain.UserRole;

public record AuthResponse(String token, String tokenType, long expiresIn, String username, UserRole role) {}

