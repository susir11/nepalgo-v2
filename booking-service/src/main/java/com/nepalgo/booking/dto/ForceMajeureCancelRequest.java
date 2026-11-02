package com.nepalgo.booking.dto;

import jakarta.validation.constraints.NotBlank;

public record ForceMajeureCancelRequest(@NotBlank String reason) {}