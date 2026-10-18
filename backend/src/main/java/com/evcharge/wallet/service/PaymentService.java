package com.evcharge.wallet.service;

import com.evcharge.wallet.gateway.GatewayTransaction;
import com.evcharge.wallet.gateway.PaymentGateway;
import com.evcharge.wallet.gateway.PaymentMethod;
import com.evcharge.wallet.model.TopUpResult;
import java.math.BigDecimal;
import org.springframework.stereotype.Service;

@Service
public class PaymentService {

    private final PaymentGateway gateway;
    private final WalletService walletService;

    public PaymentService(PaymentGateway gateway, WalletService walletService) {
        this.gateway = gateway;
        this.walletService = walletService;
    }

    public TopUpResult topUp(long userId, BigDecimal amount, PaymentMethod method) {
        GatewayTransaction txn = gateway.charge(method, amount);
        BigDecimal newBalance = walletService.creditTopUp(userId, amount);
        return new TopUpResult(txn.id(), amount, newBalance, txn.at());
    }
}