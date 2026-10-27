package com.nepalgo.auth.dto;

public record AuthTokenResponse(
        String accessToken,
        String refreshToken,
        String nepalgoId,   // formatted "#### ####" for display, per section 5a
        boolean isNewUser
) {}