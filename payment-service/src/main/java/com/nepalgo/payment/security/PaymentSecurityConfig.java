package com.nepalgo.payment.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpMethod;
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

import java.util.List;

@Configuration
public class PaymentSecurityConfig {

    @Value("${nepalgo.jwt.staff.public-key-path}")
    private String staffPublicKeyPath;

    // Staff chain only for now. Step 3 adds a customer chain (customers view
    // their own bill) and Step 5 an /internal/** chain for service-to-service.
    @Bean
    public SecurityFilterChain staffSecurityFilterChain(HttpSecurity http) throws Exception {
        http
                .securityMatcher("/api/admin/**")
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Reading rates/settings: both staff roles.
                        .requestMatchers(HttpMethod.GET, "/api/admin/fx-rates/**", "/api/admin/billing-settings")
                        .hasAnyAuthority("SUPER_ADMIN", "OPS")
                        // Everything else under /api/admin (propose/approve rates, edit
                        // settings) defaults to SUPER_ADMIN only. Later steps add
                        // specific OPS-allowed matchers (e.g. bill building) above this.
                        .anyRequest().hasAuthority("SUPER_ADMIN")
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
            return NimbusJwtDecoder.withPublicKey(PemKeyLoader.loadPublicKey(staffPublicKeyPath)).build();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load staff JWT public key", e);
        }
    }

    // Reads the single "role" claim as the authority (our tokens carry no OAuth scopes).
    @Bean
    public Converter<Jwt, AbstractAuthenticationToken> staffJwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            String role = jwt.getClaimAsString("role");
            return role == null ? List.of() : List.of(new SimpleGrantedAuthority(role));
        });
        return converter;
    }
}