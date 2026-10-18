package com.evcharge.wallet.model;
import java.math.BigDecimal;

/** finalAmount = cost of the energy used; shortfall = part of it the wallet could not cover (not collected). */
public record SettlementResult(BigDecimal finalAmount, BigDecimal refunded, BigDecimal extra, BigDecimal shortfall, BigDecimal newBalance) {}
