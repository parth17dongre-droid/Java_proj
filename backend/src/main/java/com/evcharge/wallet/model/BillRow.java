package com.evcharge.wallet.model;

import java.math.BigDecimal;

public record BillRow(
        long billId,
        long bookingId,
        long userId,
        BigDecimal ratePerKwh,
        BigDecimal advanceAmount,
        String paymentStatus,
        String bookingStatus
) {}