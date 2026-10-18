package com.evcharge.wallet.model;
import java.math.BigDecimal;
import java.time.LocalDateTime;
public record PendingBill(long billId, String chargerCode, String chargerType, LocalDateTime startTime, BigDecimal advanceAmount) {}