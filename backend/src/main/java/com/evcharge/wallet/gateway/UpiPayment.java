package com.evcharge.wallet.gateway;

import com.evcharge.wallet.exception.PaymentDeclinedException;
import java.math.BigDecimal;

public class UpiPayment implements PaymentMethod {
    private final String upiId;

    public UpiPayment(String upiId) {
        this.upiId = upiId;
    }

    @Override
    public String name() {
        return "UPI";
    }

    @Override
    public String maskedInstrument() {
        return upiId;
    }

    @Override
    public void authorize(BigDecimal amount) {
        if ("fail@okbank".equalsIgnoreCase(upiId)) {
            throw new PaymentDeclinedException("UPI payment collection request declined");
        }
    }
}