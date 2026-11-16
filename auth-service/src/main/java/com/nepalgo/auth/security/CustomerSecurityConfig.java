package com.nepalgo.auth.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class CustomerSecurityConfig {

    @Value("${nepalgo.jwt.customer.public-key-path}")
    private String publicKeyPath;

    // securityMatcher scopes this chain to ONLY customer paths, so it no
    // longer competes with the staff chain for "any request" — the two
    // can coexist because neither claims the whole surface anymore.
    @Bean
    @Order(2)
    public SecurityFilterChain customerSecurityFilterChain(HttpSecurity http) throws Exception {
        http
                .securityMatcher("/api/v1/auth/**")
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/v1/auth/firebase/exchange", "/api/v1/auth/refresh", "/api/v1/auth/logout").permitAll()
                        .anyRequest().authenticated()
                )
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt.decoder(customerJwtDecoder())));
        return http.build();
    }

    @Bean
    public JwtDecoder customerJwtDecoder() {
        try {
            var publicKey = PemKeyLoader.loadPublicKey(publicKeyPath);
            return AudienceDecoders.withAudience(NimbusJwtDecoder.withPublicKey(publicKey).build(), "nepalgo-customer");
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load customer JWT public key", e);
        }
    }
}