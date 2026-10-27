package com.nepalgo.auth.staff;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

// Runs once at every startup, but only actually DOES anything the very
// first time — once a SUPER_ADMIN exists, this becomes a no-op forever.
// This is how the very first staff account gets created, since there's
// no other staff member around yet to create it through the normal
// "SUPER_ADMIN creates staff" flow.
@Component
@RequiredArgsConstructor
public class StaffBootstrap implements ApplicationRunner {

    private final StaffUserRepository staffUserRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${nepalgo.bootstrap.admin-email:}")
    private String bootstrapEmail;

    @Value("${nepalgo.bootstrap.admin-password:}")
    private String bootstrapPassword;

    @Override
    public void run(ApplicationArguments args) {
        if (staffUserRepository.count() > 0) {
            return; // already bootstrapped, or staff created normally since
        }
        if (bootstrapEmail.isBlank() || bootstrapPassword.isBlank()) {
            // No env vars set — fine on a machine that isn't meant to
            // bootstrap (e.g. CI running tests). Just skip silently.
            return;
        }

        StaffUser admin = new StaffUser();
        admin.setEmail(bootstrapEmail);
        admin.setPasswordHash(passwordEncoder.encode(bootstrapPassword));
        admin.setRole(StaffRole.SUPER_ADMIN);
        admin.setMustChangePassword(true);
        admin.setMfaEnabled(false); // enrolls on first login, per D4
        staffUserRepository.save(admin);
    }
}