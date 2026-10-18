package com.evcharge.wallet.model;
import java.math.BigDecimal;
import java.time.LocalDateTime;
public record WalletTxn(long txnId, long walletId, Long billId, String type, BigDecimal amount, BigDecimal balanceAfter, LocalDateTime createdAt) {
}