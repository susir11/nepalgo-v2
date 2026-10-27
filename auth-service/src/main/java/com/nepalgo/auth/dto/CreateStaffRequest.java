package com.nepalgo.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateStaffRequest(
        @NotBlank @Email String email,
        @NotBlank String temporaryPassword,
        @NotNull String role // "SUPER_ADMIN" or "OPS"
) {}