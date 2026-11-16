package com.nepalgo.payment.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.http.HttpStatus;

import java.util.List;

// Three separate filter chains, one per kind of caller, chosen by URL prefix
// (securityMatcher) and evaluated in @Order. Each chain trusts exactly ONE
// credential type, so a customer token can never open an admin door, and a
// staff token can never be replayed against a customer endpoint.
@Configuration
public class PaymentSecurityConfig {

    // Audiences the auth-service stamps on full (post-MFA) tokens.
    private static final String STAFF_AUDIENCE = "nepalgo-admin";
    private static final String CUSTOMER_AUDIENCE = "nepalgo-customer";

    @Value("${nepalgo.jwt.staff.public-key-path}")
    private String staffPublicKeyPath;

    @Value("${nepalgo.jwt.customer.public-key-path}")
    private String customerPublicKeyPath;

    @Value("${nepalgo.internal.api-key}")
    private String internalApiKey;

    @Bean
    @Order(1)
    public SecurityFilterChain staffSecurityFilterChain(HttpSecurity http) throws Exception {
        http
                .securityMatcher("/api/admin/**")
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Reading rates/settings: both staff roles.
                        .requestMatchers(HttpMethod.GET, "/api/admin/fx-rates/**", "/api/admin/billing-settings")
                        .hasAnyAuthority("SUPER_ADMIN", "OPS")
                        // Building/editing bills is day-to-day booking work: OPS too.
                        .requestMatchers("/api/admin/bills/**", "/api/admin/receipts/**",
                                "/api/admin/refund-requests/**").hasAnyAuthority("SUPER_ADMIN", "OPS")
                        // Ledger is readable by OPS; issuing/editing/voiding credit is SUPER_ADMIN (below).
                        .requestMatchers(HttpMethod.GET, "/api/admin/store-credits/**").hasAnyAuthority("SUPER_ADMIN", "OPS")
                        // Everything else (propose/approve FX, VAT/settings, issue/edit/void credit): SUPER_ADMIN only.
                        .anyRequest().hasAuthority("SUPER_ADMIN")
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
                .securityMatcher("/api/bills/**", "/api/store-credits/**")
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt.decoder(customerJwtDecoder())));
        return http.build();
    }

    @Bean
    @Order(3)
    public SecurityFilterChain internalSecurityFilterChain(HttpSecurity http) throws Exception {
        http
                .securityMatcher("/internal/**")
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // NOT registered as a @Bean on purpose: a Filter bean would be
                // auto-added to EVERY request by Spring Boot, not just this chain.
                .addFilterBefore(new InternalApiKeyFilter(internalApiKey), BasicAuthenticationFilter.class)
                .authorizeHttpRequests(auth -> auth.anyRequest().hasAuthority("INTERNAL"))
                .exceptionHandling(e -> e.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)));
        return http.build();
    }

    @Bean
    public JwtDecoder staffJwtDecoder() {
        // Defence in depth: the MFA "pending" token (aud nepalgo-admin-pending)
        // is signed with the SAME staff key, so the signature alone can't tell
        // it apart from a real access token. A half-logged-in admin (password
        // OK, TOTP not yet entered) must never touch money endpoints — like an
        // OTP-pending session that must not be able to release a payout.
        return decoder(staffPublicKeyPath, STAFF_AUDIENCE, "staff");
    }

    @Bean
    public JwtDecoder customerJwtDecoder() {
        return decoder(customerPublicKeyPath, CUSTOMER_AUDIENCE, "customer");
    }

    private static JwtDecoder decoder(String keyPath, String audience, String label) {
        try {
            NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(PemKeyLoader.loadPublicKey(keyPath)).build();
            OAuth2TokenValidator<Jwt> aud = new JwtClaimValidator<List<String>>(
                    JwtClaimNames.AUD, a -> a != null && a.contains(audience));
            // createDefault() = expiry/not-before checks; we add the audience check.
            decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(JwtValidators.createDefault(), aud));
            return decoder;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load " + label + " JWT public key", e);
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
