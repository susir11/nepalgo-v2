package com.nepalgo.booking.booking;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;

// D1: NEPALGO- + 6 chars, uppercase letters and digits, excluding visually
// ambiguous characters (0/O, 1/I) — same reasoning as the NepalGo ID:
// SecureRandom for unpredictability, DB-constraint-backed retry rather
// than a pre-check, since the unique constraint is the real source of truth.
@Component
@RequiredArgsConstructor
public class VoucherCodeGenerator {

    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"; // no 0/O/1/I
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int MAX_ATTEMPTS = 10;

    private final BookingRepository bookingRepository;

    public String generate() {
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            String candidate = "NEPALGO-" + randomSixChars();
            if (!bookingRepository.existsByVoucherCode(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("Could not generate a unique voucher code after " + MAX_ATTEMPTS + " attempts");
    }

    private String randomSixChars() {
        StringBuilder sb = new StringBuilder(6);
        for (int i = 0; i < 6; i++) {
            sb.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }
}