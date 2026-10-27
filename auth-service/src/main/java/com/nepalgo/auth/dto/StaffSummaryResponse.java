package com.nepalgo.auth.dto;

public record StaffSummaryResponse(
        Long id, String email, String role, boolean mfaEnabled,
        boolean active, boolean mustChangePassword
) {}