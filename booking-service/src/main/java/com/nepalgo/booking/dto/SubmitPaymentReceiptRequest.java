package com.nepalgo.booking.dto;

import jakarta.validation.constraints.NotBlank;

public record SubmitPaymentReceiptRequest(
        @NotBlank String paymentReceiptImageUrl,
        @NotBlank String paymentReceiptCode
) {}