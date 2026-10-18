package com.evcharge.wallet.gateway;

import com.evcharge.wallet.exception.PaymentDeclinedException;
import java.math.BigDecimal;

public class CardPayment implements PaymentMethod {
    private final String cardNumber;
    private final String expiry;
    private final String cvv;

    public CardPayment(String cardNumber, String expiry, String cvv) {
        this.cardNumber = cardNumber;
        this.expiry = expiry;
        this.cvv = cvv;
    }

    @Override
    public String name() {
        return "CARD";
    }

    @Override
    public String maskedInstrument() {
        if (cardNumber == null || cardNumber.length() < 4) return "****";
        return "Card ending in " + cardNumber.substring(cardNumber.length() - 4);
    }

    @Override
    public void authorize(BigDecimal amount) {
        // Simulated test card logic from README
        if ("4000000000000002".equals(cardNumber.replaceAll("\\s+", ""))) {
            throw new PaymentDeclinedException("Card was declined by the bank");
        }
    }
}