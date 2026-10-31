package com.securepay.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtToUserAuthenticationConverterTest {
    @Test
    void preservesAccountPrincipalAndAuthoritiesWithoutCredentials() {
        UserDetails user = User.withUsername("customer-123").password("unused").roles("CUSTOMER").build();
        var converter = new JwtToUserAuthenticationConverter(name -> user);
        var authentication = converter.convert(token("CUSTOMER"));
        assertThat(authentication.getPrincipal()).isSameAs(user);
        assertThat(authentication.getName()).isEqualTo("customer-123");
        assertThat(authentication.getCredentials()).isNull();
        assertThat(authentication.isAuthenticated()).isTrue();
        assertThat(authentication.getAuthorities()).containsExactlyInAnyOrderElementsOf(user.getAuthorities());
    }

    @ParameterizedTest
    @ValueSource(strings = {"disabled", "locked", "expired", "credentialsExpired", "wrongRole"})
    void rejectsUnusableAccountsAndRoleChanges(String state) {
        UserDetails user = User.withUsername("customer-123").password("unused")
                .roles(state.equals("wrongRole") ? "ADMIN" : "CUSTOMER")
                .disabled(state.equals("disabled")).accountLocked(state.equals("locked"))
                .accountExpired(state.equals("expired"))
                .credentialsExpired(state.equals("credentialsExpired")).build();
        var converter = new JwtToUserAuthenticationConverter(name -> user);
        assertThatThrownBy(() -> converter.convert(token("CUSTOMER")))
                .isInstanceOf(InvalidBearerTokenException.class).hasMessage("Invalid token");
    }

    @Test
    void unknownAccountsProduceAuthenticationFailure() {
        var converter = new JwtToUserAuthenticationConverter(name -> {
            throw new UsernameNotFoundException("Account lookup details");
        });
        assertThatThrownBy(() -> converter.convert(token("CUSTOMER")))
                .isInstanceOf(InvalidBearerTokenException.class)
                .hasMessage("Invalid token");
    }

    private static Jwt token(String role) {
        return Jwt.withTokenValue("test").header("alg", "HS256")
                .subject("customer-123").claim("role", role).build();
    }
}
