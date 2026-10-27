package com.nepalgo.auth.security;

import com.nepalgo.auth.customer.Customer;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.security.interfaces.RSAPrivateKey;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;

// Issues RS256-signed access tokens for the CUSTOMER audience only.
// Staff tokens (Step 5) get their own separate instance of this same
// pattern with a different keypair — the two audiences must never be
// interchangeable, per D2.
@Service
@RequiredArgsConstructor
public class CustomerJwtService {

    private final CustomerJwtProperties properties;
    private RSAPrivateKey privateKey;

    private static final SecureRandom RANDOM = new SecureRandom();

    private RSAPrivateKey privateKey() throws Exception {
        if (privateKey == null) {
            privateKey = PemKeyLoader.loadPrivateKey(properties.getPrivateKeyPath());
        }
        return privateKey;
    }

    public String generateAccessToken(Customer customer) {
        try {
            Instant now = Instant.now();
            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .subject(customer.getId().toString())
                    .claim("nepalgoId", customer.getNepalgoId())
                    .audience("nepalgo-customer")
                    .issuer("nepalgo-auth-service")
                    .issueTime(Date.from(now))
                    .expirationTime(Date.from(now.plusSeconds(properties.getAccessTokenTtlMinutes() * 60L)))
                    .build();

            SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claims);
            jwt.sign(new RSASSASigner(privateKey()));
            return jwt.serialize();
        } catch (Exception e) {
            // A signing failure here means the service is fundamentally
            // misconfigured (bad key file) — fail loudly, not silently.
            throw new IllegalStateException("Failed to sign access token", e);
        }
    }

    // Refresh tokens are NOT JWTs — they're opaque random strings. Only
    // their SHA-256 hash is ever stored (customer_refresh_tokens.token_hash),
    // same principle as password hashing: even a full DB leak can't be used
    // to forge a session.
    public String generateOpaqueRefreshToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public String hashToken(String rawToken) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawToken.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}