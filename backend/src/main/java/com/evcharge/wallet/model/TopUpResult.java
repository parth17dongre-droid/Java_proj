package com.evcharge.wallet.model;
import java.math.BigDecimal;
import java.time.Instant;
public record TopUpResult(String gatewayTxnId, BigDecimal amount, BigDecimal newBalance, Instant timestamp) {}