package com.securepay.auth.service;

import com.securepay.auth.domain.UserAccount;
import com.securepay.auth.domain.UserRole;
import com.securepay.auth.repository.InMemoryUserAccountRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class AuthService {

    private final InMemoryUserAccountRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthService(InMemoryUserAccountRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public UserAccount register(String username, String password, UserRole role) {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("username must not be empty");
        }
        if (password == null || password.isBlank()) {
            throw new IllegalArgumentException("password must not be empty");
        }
        if (role == null) {
            throw new IllegalArgumentException("role is required");
        }

        String passwordHash = passwordEncoder.encode(password);
        return userRepository.save(new UserAccount(UUID.randomUUID(), username, passwordHash, role));
    }

    public boolean authenticate(String username, String password) {
        return authenticateUser(username, password).isPresent();
    }

    public java.util.Optional<UserAccount> authenticateUser(String username, String password) {
        if (username == null || password == null) {
            return java.util.Optional.empty();
        }
        return userRepository.findByUsername(username)
                .filter(user -> passwordEncoder.matches(password, user.passwordHash()));
    }
}
