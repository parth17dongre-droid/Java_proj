package com.evcharge.wallet.model;

import java.math.BigDecimal;

public record WalletRow(long walletId, long userId, BigDecimal balance, long version) {}