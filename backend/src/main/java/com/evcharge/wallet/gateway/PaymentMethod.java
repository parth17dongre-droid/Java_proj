package com.evcharge.wallet.gateway;

import java.math.BigDecimal;

public interface PaymentMethod {
    String name();
    String maskedInstrument();
    void authorize(BigDecimal amount);
}