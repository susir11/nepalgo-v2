package com.nepalgo.auth.security;

import com.nepalgo.auth.staff.StaffUser;
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

@Service
@RequiredArgsConstructor
public class StaffJwtService {

    private final StaffJwtProperties properties;
    private RSAPrivateKey privateKey;
    private static final SecureRandom RANDOM = new SecureRandom();

    private RSAPrivateKey privateKey() throws Exception {
        if (privateKey == null) {
            privateKey = PemKeyLoader.loadPrivateKey(properties.getPrivateKeyPath());
        }
        return privateKey;
    }

    public String generateAccessToken(StaffUser staff) {
        try {
            Instant now = Instant.now();
            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .subject(staff.getId().toString())
                    .claim("role", staff.getRole().name())
                    .claim("email", staff.getEmail())
                    .audience("nepalgo-admin") // distinct from "nepalgo-customer" — D2
                    .issuer("nepalgo-auth-service")
                    .issueTime(Date.from(now))
                    .expirationTime(Date.from(now.plusSeconds(properties.getAccessTokenTtlMinutes() * 60L)))
                    .build();

            SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claims);
            jwt.sign(new RSASSASigner(privateKey()));
            return jwt.serialize();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to sign staff access token", e);
        }
    }

    public String generateOpaqueRefreshToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public String hashToken(String rawToken) {
        try {
            var digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawToken.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    public String generatePendingMfaToken(StaffUser staff) {
        try {
            Instant now = Instant.now();
            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .subject(staff.getId().toString())
                    .audience("nepalgo-admin-pending")
                    .issuer("nepalgo-auth-service")
                    .issueTime(Date.from(now))
                    .expirationTime(Date.from(now.plusSeconds(300))) // 5 minutes — just long enough to type a code
                    .build();
            SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claims);
            jwt.sign(new RSASSASigner(privateKey()));
            return jwt.serialize();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to sign pending MFA token", e);
        }
    }

    // Verifies a pending-MFA token and returns the staff ID it was issued for.
// Checks the audience explicitly — a real access token must NOT work here.
    public Long verifyPendingMfaToken(String token) {
        try {
            SignedJWT jwt = SignedJWT.parse(token);
            var verifier = new com.nimbusds.jose.crypto.RSASSAVerifier(
                    PemKeyLoader.loadPublicKey(properties.getPublicKeyPath()));
            if (!jwt.verify(verifier)) {
                throw new IllegalStateException("Signature invalid");
            }
            var claims = jwt.getJWTClaimsSet();
            if (!claims.getAudience().contains("nepalgo-admin-pending")) {
                throw new IllegalStateException("Wrong token type");
            }
            if (claims.getExpirationTime().before(new Date())) {
                throw new IllegalStateException("Expired");
            }
            return Long.parseLong(claims.getSubject());
        } catch (Exception e) {
            throw new com.nepalgo.auth.staff.InvalidPendingTokenException("Invalid or expired pending MFA token");
        }
    }
}