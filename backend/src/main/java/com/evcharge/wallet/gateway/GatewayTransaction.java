package com.evcharge.wallet.gateway;
import java.math.BigDecimal;
import java.time.Instant;
public record GatewayTransaction(String id, String method, String instrument, BigDecimal amount, String status, Instant at, String worker) {}
