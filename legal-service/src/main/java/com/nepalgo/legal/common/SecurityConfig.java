package com.nepalgo.legal.common;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.List;

// Four doors, each trusting exactly one kind of caller (checked in @Order):
//  1 /api/admin/**          staff JWT (aud nepalgo-admin); writes = SUPER_ADMIN
//  2 /api/legal/consents/** customer JWT (aud nepalgo-customer)
//  3 /internal/**           X-Internal-Api-Key (booking-service)
//  4 everything else        public read-only content (Terms, Privacy, onboarding)
@Configuration
public class SecurityConfig {

    @Value("${nepalgo.jwt.staff.public-key-path}")
    private String staffKeyPath;

    @Value("${nepalgo.jwt.customer.public-key-path}")
    private String customerKeyPath;

    @Value("${nepalgo.internal.api-key}")
    private String internalApiKey;

    @Bean
    @Order(1)
    SecurityFilterChain staff(HttpSecurity http) throws Exception {
        stateless(http.securityMatcher("/api/admin/**"))
                .authorizeHttpRequests(a -> a
                        .requestMatchers(HttpMethod.GET, "/api/admin/**").hasAnyAuthority("SUPER_ADMIN", "OPS")
                        // Publishing legal text changes what customers agree to: SUPER_ADMIN only.
                        .anyRequest().hasAuthority("SUPER_ADMIN"))
                .oauth2ResourceServer(o -> o.jwt(j -> j.decoder(staffJwtDecoder()).jwtAuthenticationConverter(roleConverter())));
        return http.build();
    }

    @Bean
    @Order(2)
    SecurityFilterChain customer(HttpSecurity http) throws Exception {
        stateless(http.securityMatcher("/api/legal/consents/**"))
                .authorizeHttpRequests(a -> a.anyRequest().authenticated())
                .oauth2ResourceServer(o -> o.jwt(j -> j.decoder(customerJwtDecoder())));
        return http.build();
    }

    @Bean
    @Order(3)
    SecurityFilterChain internal(HttpSecurity http) throws Exception {
        stateless(http.securityMatcher("/internal/**"))
                .addFilterBefore(new InternalKeyFilter(internalApiKey), BasicAuthenticationFilter.class)
                .authorizeHttpRequests(a -> a.anyRequest().hasAuthority("INTERNAL"))
                .exceptionHandling(e -> e.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)));
        return http.build();
    }

    @Bean
    @Order(4)
    SecurityFilterChain publicContent(HttpSecurity http) throws Exception {
        stateless(http)
                .authorizeHttpRequests(a -> a
                        .requestMatchers(HttpMethod.GET, "/api/legal/**", "/api/content/**", "/actuator/health/**", "/actuator/info").permitAll()
                        .anyRequest().denyAll());   // default-deny: anything not listed above is closed
        return http.build();
    }

    private static HttpSecurity stateless(HttpSecurity http) throws Exception {
        return http.csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
    }

    @Bean
    JwtDecoder staffJwtDecoder() {
        return decoder(staffKeyPath, "nepalgo-admin");
    }

    @Bean
    JwtDecoder customerJwtDecoder() {
        return decoder(customerKeyPath, "nepalgo-customer");
    }

    // Signature (public key) + expiry + audience. The audience check is what keeps
    // an MFA-pending staff token (same key, aud nepalgo-admin-pending) out.
    private static JwtDecoder decoder(String keyPath, String audience) {
        try {
            String pem = Files.readString(Path.of(keyPath))
                    .replaceAll("-----BEGIN (.*)-----", "").replaceAll("-----END (.*)-----", "").replaceAll("\\s", "");
            RSAPublicKey key = (RSAPublicKey) KeyFactory.getInstance("RSA")
                    .generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(pem)));
            NimbusJwtDecoder d = NimbusJwtDecoder.withPublicKey(key).build();
            OAuth2TokenValidator<Jwt> aud = new JwtClaimValidator<List<String>>(JwtClaimNames.AUD, a -> a != null && a.contains(audience));
            d.setJwtValidator(new DelegatingOAuth2TokenValidator<>(JwtValidators.createDefault(), aud));
            return d;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load JWT public key " + keyPath, e);
        }
    }

    // Staff tokens carry a single "role" claim (SUPER_ADMIN / OPS), no OAuth scopes.
    @Bean
    Converter<Jwt, AbstractAuthenticationToken> roleConverter() {
        JwtAuthenticationConverter c = new JwtAuthenticationConverter();
        c.setJwtGrantedAuthoritiesConverter(jwt -> {
            String role = jwt.getClaimAsString("role");
            return role == null ? List.of() : List.of(new SimpleGrantedAuthority(role));
        });
        return c;
    }

    // Constant-time comparison of the shared service secret (no timing leak).
    static class InternalKeyFilter extends OncePerRequestFilter {
        private final byte[] expected;

        InternalKeyFilter(String key) {
            if (key == null || key.isBlank()) throw new IllegalStateException("nepalgo.internal.api-key must be set");
            this.expected = key.getBytes(StandardCharsets.UTF_8);
        }

        @Override
        protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
                throws ServletException, IOException {
            String given = req.getHeader("X-Internal-Api-Key");
            if (given != null && MessageDigest.isEqual(expected, given.getBytes(StandardCharsets.UTF_8))) {
                SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                        "internal-service", null, List.of(new SimpleGrantedAuthority("INTERNAL"))));
            }
            chain.doFilter(req, res);
        }
    }
}
