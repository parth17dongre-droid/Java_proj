package com.evcharge.wallet.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record TopUpRequest(
        @NotNull(message = "Amount is required")
        @DecimalMin(value = "10.00", message = "Minimum top-up amount is ₹10")
        @DecimalMax(value = "50000.00", message = "Maximum top-up amount is ₹50,000")
        BigDecimal amount,

        @NotBlank(message = "Payment method is required")
        String method, // "CARD" or "UPI"

        String cardNumber,
        String expiry,
        String cvv,
        String upiId
) {}