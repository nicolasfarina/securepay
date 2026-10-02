package com.securepay.auth.api;

import com.securepay.auth.domain.UserAccount;
import com.securepay.auth.domain.UserRole;
import com.securepay.auth.service.AuthService;
import com.securepay.security.JwtService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/auth")
public class AuthController {
    private final AuthService authService;
    private final JwtService jwtService;
    private final long expiresIn;

    public AuthController(AuthService authService, JwtService jwtService,
                          @Value("${securepay.security.jwt-expiration-seconds:3600}") long expiresIn) {
        this.authService = authService;
        this.jwtService = jwtService;
        this.expiresIn = expiresIn;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse register(@Valid @RequestBody RegisterRequest request) {
        try {
            return response(authService.register(request.username(), request.password(), UserRole.CUSTOMER));
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, ex.getMessage());
        }
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        UserAccount account = authService.authenticateUser(request.username(), request.password())
                .orElseThrow(() -> new BadCredentialsException("Invalid username or password"));
        return response(account);
    }

    private AuthResponse response(UserAccount account) {
        return new AuthResponse(jwtService.issueToken(account), "Bearer", expiresIn, account.username(), account.role());
    }
}

