package com.nepalgo.auth.dto;

public record MfaEnrollStartResponse(String secret, String qrContentUri) {}