package com.nepalgo.auth.dto;

import jakarta.validation.constraints.NotNull;

public record ChangeRoleRequest(@NotNull String role) {}