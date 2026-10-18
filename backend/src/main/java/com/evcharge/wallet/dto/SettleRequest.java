package com.evcharge.wallet.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record SettleRequest(
        @NotNull(message = "Energy consumed (kWh) is required")
        @DecimalMin(value = "0.000", message = "Energy consumed cannot be negative")
        BigDecimal energyKwh
) {}