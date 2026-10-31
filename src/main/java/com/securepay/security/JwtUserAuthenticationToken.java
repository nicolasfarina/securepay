package com.securepay.security;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.Instant;
import java.util.Collection;

final class JwtUserAuthenticationToken extends UsernamePasswordAuthenticationToken {
    private final String tokenId;
    private final Instant expiresAt;

    JwtUserAuthenticationToken(UserDetails user, Collection<? extends GrantedAuthority> authorities,
            String tokenId, Instant expiresAt) {
        super(user, null, authorities);
        this.tokenId = tokenId;
        this.expiresAt = expiresAt;
    }

    String tokenId() {
        return tokenId;
    }

    Instant expiresAt() {
        return expiresAt;
    }
}
