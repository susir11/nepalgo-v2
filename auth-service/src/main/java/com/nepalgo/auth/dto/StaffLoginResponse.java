package com.nepalgo.auth.dto;

// mfaRequired tells the frontend whether to show the MFA code screen next.
// pendingToken is a short-lived, narrowly-scoped token proving "this
// person passed step 1 (password)" — it is NOT a real access token and
// can't be used anywhere except /mfa/verify.
public record StaffLoginResponse(boolean mfaRequired, String pendingToken, StaffAuthTokenResponse tokens) {}