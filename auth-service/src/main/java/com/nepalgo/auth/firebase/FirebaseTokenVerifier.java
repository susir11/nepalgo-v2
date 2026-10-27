package com.nepalgo.auth.firebase;

// Sits behind an interface so tests never hit Google — a fake
// implementation can be swapped in for unit/integration tests without
// touching the real Firebase project.
public interface FirebaseTokenVerifier {
    VerifiedFirebaseToken verify(String idToken);

    record VerifiedFirebaseToken(String uid, String email, String name) {}
}