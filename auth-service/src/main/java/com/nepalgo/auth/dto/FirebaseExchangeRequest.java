package com.nepalgo.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record FirebaseExchangeRequest(@NotBlank String idToken) {}