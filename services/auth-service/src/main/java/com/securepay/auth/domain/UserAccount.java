package com.securepay.auth.domain;

import java.util.Objects;
import java.util.UUID;

public record UserAccount(UUID id, String username, String passwordHash, UserRole role) {

    public UserAccount {
        Objects.requireNonNull(id, "id is required");
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("username must not be empty");
        }
        Objects.requireNonNull(passwordHash, "passwordHash is required");
        if (passwordHash.isBlank()) {
            throw new IllegalArgumentException("passwordHash must not be empty");
        }
        Objects.requireNonNull(role, "role is required");
    }
}
