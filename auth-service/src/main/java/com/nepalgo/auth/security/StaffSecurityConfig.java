package com.nepalgo.auth.security;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.util.List;

@Configuration
@RequiredArgsConstructor
public class StaffSecurityConfig {

    @Value("${nepalgo.jwt.staff.public-key-path}")
    private String publicKeyPath;

    private final RateLimitFilter rateLimitFilter;

    // securityMatcher scopes this chain to ONLY admin paths, so it never
    // competes with the customer chain for "any request" — the two chains
    // can coexist because neither claims the whole surface.
    @Bean
    @Order(1)
    public SecurityFilterChain staffSecurityFilterChain(HttpSecurity http) throws Exception {
        http
                .securityMatcher("/api/v1/admin/**")
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .addFilterBefore(rateLimitFilter, UsernamePasswordAuthenticationFilter.class)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/api/v1/admin/auth/login",
                                "/api/v1/admin/auth/mfa/enroll/start",
                                "/api/v1/admin/auth/mfa/enroll/confirm",
                                "/api/v1/admin/auth/mfa/verify",
                                "/api/v1/admin/auth/refresh",
                                "/api/v1/admin/auth/logout"
                        ).permitAll()
                        // D1: staff management is SUPER_ADMIN only, per section 5b.
                        // hasAuthority checks the exact string staffJwtAuthenticationConverter()
                        // below produces from the JWT's "role" claim.
                        .requestMatchers("/api/v1/admin/staff/**").hasAuthority("SUPER_ADMIN")
                        .anyRequest().authenticated()
                )
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt
                        .decoder(staffJwtDecoder())
                        .jwtAuthenticationConverter(staffJwtAuthenticationConverter())
                ));
        return http.build();
    }

    @Bean
    public JwtDecoder staffJwtDecoder() {
        try {
            var publicKey = PemKeyLoader.loadPublicKey(publicKeyPath);
            return NimbusJwtDecoder.withPublicKey(publicKey).build();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load staff JWT public key", e);
        }
    }

    // Tells Spring Security to read authorities from our custom "role"
    // claim instead of the default "scope"/"scp" claims it expects — our
    // tokens don't use OAuth2 scopes, just a single role string.
    @Bean
    public Converter<Jwt, AbstractAuthenticationToken> staffJwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            String role = jwt.getClaimAsString("role");
            if (role == null) {
                return List.of();
            }
            return List.of(new SimpleGrantedAuthority(role));
        });
        return converter;
    }
}