package com.nepalgo.auth.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class PasswordConfig {

    // D3: cost 12, above Spring's own default of 10 — each unit roughly
    // doubles the hashing time, which is exactly the point: it should be
    // expensive for an attacker brute-forcing a leaked hash, while still
    // cheap enough (a few hundred ms) that a real login doesn't notice.
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }
}