package com.nepalgo.booking.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record IdentityRequest(
        @NotBlank String legalName,       // as on passport
        @NotBlank String passportNumber,
        @NotBlank @Email String email
) {}