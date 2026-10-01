package com.securepay.auth.service;

import com.securepay.auth.domain.UserAccount;
import com.securepay.auth.domain.UserRole;
import com.securepay.auth.repository.InMemoryUserAccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.*;

class AuthServiceTest {

    private PasswordEncoder passwordEncoder;
    private AuthService authService;
    private InMemoryUserAccountRepository userRepository;

    @BeforeEach
    void setUp() {
        passwordEncoder = new BCryptPasswordEncoder();
        userRepository = new InMemoryUserAccountRepository();
        authService = new AuthService(userRepository, passwordEncoder);
    }

    @Test
    void createsCustomerWithHashedPassword() {
        UserAccount account = authService.register("alice", "correct horse battery staple", UserRole.CUSTOMER);

        assertNotNull(account.id());
        assertEquals("alice", account.username());
        assertEquals(UserRole.CUSTOMER, account.role());
        assertNotEquals("correct horse battery staple", account.passwordHash());
        assertTrue(passwordEncoder.matches("correct horse battery staple", account.passwordHash()));
        assertEquals(account, userRepository.findByUsername("alice").orElseThrow());
    }

    @Test
    void rejectsNullOrEmptyUsername() {
        assertThrows(IllegalArgumentException.class,
                () -> authService.register("", "password", UserRole.CUSTOMER));
        assertThrows(IllegalArgumentException.class,
                () -> authService.register("  ", "password", UserRole.CUSTOMER));
        assertThrows(IllegalArgumentException.class,
                () -> authService.register(null, "password", UserRole.CUSTOMER));
    }

    @Test
    void rejectsMissingRole() {
        assertThrows(IllegalArgumentException.class, () -> authService.register("alice", "password", null));
    }

    @Test
    void authenticatesMatchingPassword() {
        authService.register("alice", "secret", UserRole.CUSTOMER);

        assertTrue(authService.authenticate("alice", "secret"));
    }

    @Test
    void rejectsWrongPasswordAndUnknownUser() {
        authService.register("alice", "secret", UserRole.CUSTOMER);

        assertFalse(authService.authenticate("alice", "wrong"));
        assertFalse(authService.authenticate("missing", "secret"));
    }
}
