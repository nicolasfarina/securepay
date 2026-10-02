package com.securepay.auth.repository;

import com.securepay.auth.domain.UserAccount;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
public class InMemoryUserAccountRepository {

    private final ConcurrentMap<String, UserAccount> usersByUsername = new ConcurrentHashMap<>();

    public UserAccount save(UserAccount user) {
        UserAccount existing = usersByUsername.putIfAbsent(user.username(), user);
        if (existing != null) {
            throw new IllegalArgumentException("username is already registered");
        }
        return user;
    }

    public Optional<UserAccount> findByUsername(String username) {
        return Optional.ofNullable(usersByUsername.get(username));
    }
}
