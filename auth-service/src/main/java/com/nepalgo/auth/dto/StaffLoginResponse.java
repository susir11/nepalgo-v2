package com.nepalgo.auth.dto;

// mfaRequired tells the frontend whether to show the MFA code screen next.
// pendingToken is a short-lived, narrowly-scoped token proving "this
// person passed step 1 (password)" — it is NOT a real access token and
// can't be used anywhere except /mfa/verify.
// mfaEnrollmentRequired: a SUPER_ADMIN without MFA gets NO access token at all,
// only a pending token that is good for exactly one thing: enrolling TOTP.
public record StaffLoginResponse(boolean mfaRequired, String pendingToken, StaffAuthTokenResponse tokens,
                                 boolean mfaEnrollmentRequired) {}