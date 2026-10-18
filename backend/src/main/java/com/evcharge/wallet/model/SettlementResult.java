package com.evcharge.wallet.model;
import java.math.BigDecimal;
public record SettlementResult(BigDecimal finalAmount, BigDecimal refunded, BigDecimal extra, BigDecimal newBalance) {}