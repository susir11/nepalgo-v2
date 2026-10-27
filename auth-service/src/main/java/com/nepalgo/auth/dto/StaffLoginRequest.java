package com.nepalgo.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record StaffLoginRequest(@NotBlank String email, @NotBlank String password) {}