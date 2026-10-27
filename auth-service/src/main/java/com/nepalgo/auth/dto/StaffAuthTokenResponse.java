package com.nepalgo.auth.dto;

public record StaffAuthTokenResponse(String accessToken, String refreshToken, String role) {}