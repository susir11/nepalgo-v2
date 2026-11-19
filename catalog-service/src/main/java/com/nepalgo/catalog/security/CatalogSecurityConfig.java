package com.nepalgo.catalog.security;

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

import java.util.List;

@Configuration
public class CatalogSecurityConfig {

    @Value("${nepalgo.jwt.staff.public-key-path}")
    private String publicKeyPath;

    /**
     * Public catalogue: READ-ONLY and token-blind.
     *
     * Why a separate chain: this service only knows the STAFF signing key. In a chain
     * with oauth2ResourceServer, Spring validates ANY "Authorization: Bearer" header,
     * even on permitAll() routes. The app sends the CUSTOMER's token on every request
     * once logged in, so Explore got 401 "Invalid signature" for every signed-in user.
     * This chain has no resource server at all, so a customer token is simply ignored.
     * (Same pattern as booking-service's public reviews chain.)
     * Like a shop window: anyone may look; showing your bank card doesn't change that,
     * and a card the shop can't read must not get you thrown out.
     */
    @Bean
    @Order(1)
    public SecurityFilterChain publicCatalogFilterChain(HttpSecurity http) throws Exception {
        http
                .securityMatcher("/api/destinations/**")
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/destinations/**").permitAll()
                        .anyRequest().denyAll());   // no writes exist here; refuse anything that isn't a read
        return http.build();
    }

    @Bean
    @Order(2)
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/**").permitAll()
                        // (Public GET /api/destinations/** is handled by publicCatalogFilterChain.)
                        // Both SUPER_ADMIN and OPS cover "day-to-day... catalogue
                        // work" per spec section 5b — unlike staff management,
                        // this is NOT SUPER_ADMIN-only.
                        .requestMatchers("/api/admin/destinations/**").hasAnyAuthority("SUPER_ADMIN", "OPS")
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
            NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(publicKey).build();
            // Defence in depth (same as booking/payment): the MFA-pending token is
            // signed with the same staff key; only "aud" tells it apart from a
            // real post-MFA staff token.
            org.springframework.security.oauth2.core.OAuth2TokenValidator<Jwt> aud =
                    new org.springframework.security.oauth2.jwt.JwtClaimValidator<List<String>>(
                            org.springframework.security.oauth2.jwt.JwtClaimNames.AUD,
                            a -> a != null && a.contains("nepalgo-admin"));
            decoder.setJwtValidator(new org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator<>(
                    org.springframework.security.oauth2.jwt.JwtValidators.createDefault(), aud));
            return decoder;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load staff JWT public key", e);
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