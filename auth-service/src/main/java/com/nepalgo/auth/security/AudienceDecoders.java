package com.nepalgo.auth.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

// (1) Audience check for this service's own decoders: the MFA-pending staff
//     token shares the staff signing key, so only "aud" separates it from a
//     real post-MFA token. (2) The /internal/** chain for service-to-service calls.
@Configuration
public class AudienceDecoders {

    @Value("${nepalgo.internal.api-key}")
    private String internalApiKey;

    public static NimbusJwtDecoder withAudience(NimbusJwtDecoder decoder, String audience) {
        OAuth2TokenValidator<Jwt> aud = new JwtClaimValidator<List<String>>(JwtClaimNames.AUD, a -> a != null && a.contains(audience));
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(JwtValidators.createDefault(), aud));
        return decoder;
    }

    @Bean
    @Order(3)
    public SecurityFilterChain internalSecurityFilterChain(HttpSecurity http) throws Exception {
        http.securityMatcher("/internal/**")
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .addFilterBefore(new InternalKeyFilter(internalApiKey), BasicAuthenticationFilter.class)
                .authorizeHttpRequests(a -> a.anyRequest().hasAuthority("INTERNAL"))
                .exceptionHandling(e -> e.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)));
        return http.build();
    }

    // Constant-time comparison so response timing can't leak the key.
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
