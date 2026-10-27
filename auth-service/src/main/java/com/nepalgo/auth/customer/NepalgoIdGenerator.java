package com.nepalgo.auth.customer;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.security.SecureRandom;

// Generates an 8-digit ID, first digit 1-9 (D9: no leading zero, so it
// always displays as a full 8 digits). SecureRandom, not Random — this
// prevents an attacker from predicting the next ID from a previous one,
// same reasoning as generating a card PIN.
//
// The database UNIQUE constraint on nepalgo_id is the actual source of
// truth for collision detection — we don't pre-check for existence, we
// just retry on a constraint violation. This is the same "optimistic,
// retry-on-conflict" pattern as generating a UTR and letting the DB
// reject a duplicate rather than locking a counter table.
@Component
@RequiredArgsConstructor
public class NepalgoIdGenerator {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int MAX_ATTEMPTS = 10;

    private final CustomerRepository customerRepository;
    private final RetiredNepalgoIdRepository retiredNepalgoIdRepository;

    public String generate() {
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            String candidate = randomEightDigits();
            boolean inUse = customerRepository.existsByNepalgoId(candidate);
            boolean retired = retiredNepalgoIdRepository.existsById(candidate);
            if (!inUse && !retired) {
                return candidate;
            }
        }
        // Astronomically unlikely at 8 digits with a SecureRandom source,
        // but fail loudly rather than silently looping forever.
        throw new IllegalStateException("Could not generate a unique NepalGo ID after " + MAX_ATTEMPTS + " attempts");
    }

    private String randomEightDigits() {
        StringBuilder sb = new StringBuilder(8);
        sb.append(1 + RANDOM.nextInt(9)); // first digit 1-9
        for (int i = 0; i < 7; i++) {
            sb.append(RANDOM.nextInt(10));
        }
        return sb.toString();
    }
}