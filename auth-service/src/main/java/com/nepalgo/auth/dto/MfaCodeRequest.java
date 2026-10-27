package com.nepalgo.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record MfaCodeRequest(@NotBlank String pendingToken, @NotBlank String code) {}