package com.nepalgo.auth.security;

import dev.samstevens.totp.code.*;
import dev.samstevens.totp.secret.DefaultSecretGenerator;
import dev.samstevens.totp.time.SystemTimeProvider;
import org.springframework.stereotype.Service;

@Service
public class TotpService {

    private final CodeGenerator codeGenerator = new DefaultCodeGenerator();
    private final CodeVerifier codeVerifier = new DefaultCodeVerifier(codeGenerator, new SystemTimeProvider());

    public String generateSecret() {
        return new DefaultSecretGenerator().generate();
    }

    // Builds the otpauth:// URI an authenticator app's QR scanner reads.
    // "NepalGo Admin" is what shows up as the account label in the app.
    public String buildQrContentUri(String secret, String staffEmail) {
        return String.format(
                "otpauth://totp/NepalGo%%20Admin:%s?secret=%s&issuer=NepalGo%%20Admin",
                staffEmail, secret
        );
    }

    public boolean verify(String secret, String code) {
        return codeVerifier.isValidCode(secret, code);
    }
}