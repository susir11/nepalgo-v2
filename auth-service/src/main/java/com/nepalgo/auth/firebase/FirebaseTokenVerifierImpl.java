package com.nepalgo.auth.firebase;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseToken;
import org.springframework.stereotype.Component;

@Component
public class FirebaseTokenVerifierImpl implements FirebaseTokenVerifier {

    @Override
    public VerifiedFirebaseToken verify(String idToken) {
        try {
            FirebaseToken decoded = FirebaseAuth.getInstance().verifyIdToken(idToken);
            return new VerifiedFirebaseToken(decoded.getUid(), decoded.getEmail(), decoded.getName());
        } catch (FirebaseAuthException e) {
            // Covers expired, malformed, and revoked tokens alike — the
            // controller maps this to a generic 401, same reasoning as
            // staff login errors: don't leak which specific thing was wrong.
            throw new InvalidFirebaseTokenException("Invalid or expired Firebase ID token", e);
        }
    }
}