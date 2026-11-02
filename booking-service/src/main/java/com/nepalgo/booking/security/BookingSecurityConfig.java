package com.nepalgo.booking.security;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

import java.util.List;

@Configuration
@RequiredArgsConstructor
public class BookingSecurityConfig {

    @Value("${nepalgo.jwt.customer.public-key-path}")
    private String customerPublicKeyPath;

    @Value("${nepalgo.jwt.staff.public-key-path}")
    private String staffPublicKeyPath;

    // Staff chain first (@Order(1)) — same reasoning as auth-service's own
    // dual-chain setup: lower order is evaluated first, and being explicit
    // avoids ambiguity even though /api/admin/** and /api/bookings/** don't
    // actually overlap as path patterns.
    @Bean
    @Order(1)
    public SecurityFilterChain staffSecurityFilterChain(HttpSecurity http) throws Exception {
        http
                .securityMatcher("/api/admin/**")
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/admin/bookings/**", "/api/admin/customers/**", "/api/admin/refund-requests/**")
                        .hasAnyAuthority("SUPER_ADMIN", "OPS")
                        .anyRequest().authenticated()
                )
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt
                        .decoder(staffJwtDecoder())
                        .jwtAuthenticationConverter(staffJwtAuthenticationConverter())
                ));
        return http.build();
    }

    @Bean
    @Order(2)
    public SecurityFilterChain customerSecurityFilterChain(HttpSecurity http) throws Exception {
        http
                .securityMatcher("/api/bookings/**")
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt.decoder(customerJwtDecoder())));
        return http.build();
    }

    @Bean
    public JwtDecoder staffJwtDecoder() {
        try {
            return NimbusJwtDecoder.withPublicKey(PemKeyLoader.loadPublicKey(staffPublicKeyPath)).build();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load staff JWT public key", e);
        }
    }

    @Bean
    public JwtDecoder customerJwtDecoder() {
        try {
            return NimbusJwtDecoder.withPublicKey(PemKeyLoader.loadPublicKey(customerPublicKeyPath)).build();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load customer JWT public key", e);
        }
    }

    @Bean
    public Converter<Jwt, AbstractAuthenticationToken> staffJwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            String role = jwt.getClaimAsString("role");
            if (role == null) return List.of();
            return List.of(new SimpleGrantedAuthority(role));
        });
        return converter;
    }
}