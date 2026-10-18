package com.evcharge.wallet.exception;
public class InsufficientBalanceException extends PaymentException {
    public InsufficientBalanceException(String message) { super(message); }
}