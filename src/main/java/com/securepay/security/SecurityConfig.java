package com.securepay.security;

import jakarta.servlet.DispatcherType;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;

@Configuration
@EnableMethodSecurity
@SecurityScheme(name = "bearerAuth", type = SecuritySchemeType.HTTP, scheme = "bearer", bearerFormat = "JWT")
public class SecurityConfig {

    @Bean
    FilterRegistrationBean<RateLimitFilter> rateLimitFilterServletRegistration(RateLimitFilter rateLimitFilter) {
        FilterRegistrationBean<RateLimitFilter> registration = new FilterRegistrationBean<>(rateLimitFilter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JwtToUserAuthenticationConverter jwtAuthenticationConverter,
            RateLimitFilter rateLimitFilter,
            @Value("${springdoc.api-docs.enabled:true}") boolean apiDocsEnabled,
            @Value("${springdoc.swagger-ui.enabled:true}") boolean swaggerUiEnabled) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED))
                        .accessDeniedHandler((request, response, exception) ->
                                response.sendError(HttpStatus.FORBIDDEN.value())))
                .authorizeHttpRequests(authorize -> {
                    authorize
                            .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                            .requestMatchers("/auth/login").permitAll()
                            .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                            .requestMatchers("/actuator/metrics", "/actuator/metrics/**", "/actuator/prometheus").hasRole("ADMIN");
                    if (swaggerUiEnabled) {
                        authorize.requestMatchers("/swagger-ui.html", "/swagger-ui/**").permitAll();
                    }
                    if (apiDocsEnabled) {
                        authorize.requestMatchers("/v3/api-docs", "/v3/api-docs/**").permitAll();
                    }
                    authorize.anyRequest().authenticated();
                })
                .oauth2ResourceServer(resourceServer -> resourceServer
                        .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED))
                        .accessDeniedHandler((request, response, exception) ->
                                response.sendError(HttpStatus.FORBIDDEN.value()))
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter)))
                .addFilterAfter(rateLimitFilter, BearerTokenAuthenticationFilter.class)
                .build();
    }

    @Bean
    UserDetailsService users(
            @Value("${securepay.security.demo-password}") String demoPassword,
            PasswordEncoder passwordEncoder) {
        return new InMemoryUserDetailsManager(
                User.withUsername("customer-123")
                        .password(passwordEncoder.encode(demoPassword))
                        .roles("CUSTOMER")
                        .build(),
                User.withUsername("customer-789")
                        .password(passwordEncoder.encode(demoPassword))
                        .roles("CUSTOMER")
                        .build(),
                User.withUsername("admin-123")
                        .password(passwordEncoder.encode(demoPassword))
                        .roles("ADMIN")
                        .build());
    }

    @Bean
    AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
