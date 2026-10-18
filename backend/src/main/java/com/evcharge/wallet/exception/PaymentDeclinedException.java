package com.evcharge.wallet.exception;
public class PaymentDeclinedException extends PaymentException {
    public PaymentDeclinedException(String message) { super(message); }
}