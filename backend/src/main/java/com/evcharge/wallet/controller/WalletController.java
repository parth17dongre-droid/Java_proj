package com.evcharge.wallet.controller;

import com.evcharge.wallet.dto.SettleRequest;
import com.evcharge.wallet.dto.TopUpRequest;
import com.evcharge.wallet.exception.PaymentException;
import com.evcharge.wallet.gateway.CardPayment;
import com.evcharge.wallet.gateway.GatewayTransaction;
import com.evcharge.wallet.gateway.PaymentGateway;
import com.evcharge.wallet.gateway.PaymentMethod;
import com.evcharge.wallet.gateway.UpiPayment;
import com.evcharge.wallet.model.PendingBill;
import com.evcharge.wallet.model.SettlementResult;
import com.evcharge.wallet.model.TopUpResult;
import com.evcharge.wallet.model.WalletTxn;
import com.evcharge.wallet.service.PaymentService;
import com.evcharge.wallet.service.WalletService;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.security.Principal;
import java.util.List;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST endpoints for the wallet and payments.
 *
 * The logged-in user comes from the JWT, never from the request body. This code assumes
 * the JWT subject (Principal name) is the user's EMAIL. If your auth code stores the
 * numeric user id instead, change currentUserId() only.
 */
@RestController
@RequestMapping("/api")
public class WalletController {

    private final WalletService walletService;
    private final PaymentService paymentService;
    private final PaymentGateway gateway;

    public WalletController(WalletService walletService, PaymentService paymentService, PaymentGateway gateway) {
        this.walletService = walletService;
        this.paymentService = paymentService;
        this.gateway = gateway;
    }

    @GetMapping("/wallet")
    public Map<String, BigDecimal> balance(Principal principal) {
        return Map.of("balance", walletService.getBalance(currentUserId(principal)));
    }

    @GetMapping("/wallet/transactions")
    public List<WalletTxn> transactions(Principal principal, @RequestParam(defaultValue = "50") int limit) {
        return walletService.getStatement(currentUserId(principal), limit);
    }

    @GetMapping("/wallet/pending-bills")
    public List<PendingBill> pendingBills(Principal principal) {
        return walletService.getPendingBills(currentUserId(principal));
    }

    @PostMapping("/wallet/topup")
    public TopUpResult topUp(Principal principal, @Valid @RequestBody TopUpRequest req) {
        PaymentMethod method = switch (req.method()) {
            case "CARD" -> new CardPayment(req.cardNumber(), req.expiry(), req.cvv());
            case "UPI" -> new UpiPayment(req.upiId());
            default -> throw new PaymentException("Unsupported payment method");
        };
        return paymentService.topUp(currentUserId(principal), req.amount(), method);
    }

    /** Pay the advance of a bill from the wallet; this is what unlocks the charger. */
    @PostMapping("/bills/{billId}/pay")
    public Map<String, Object> payAdvance(Principal principal, @PathVariable long billId) {
        BigDecimal newBalance = walletService.payAdvance(billId, currentUserId(principal));
        return Map.of("billId", billId, "status", "PAID", "newBalance", newBalance);
    }

    /** Called when the charging session ends (admin / charger side). */
    @PostMapping("/bills/{billId}/settle")
    @PreAuthorize("hasRole('ADMIN')")
    public SettlementResult settle(@PathVariable long billId, @Valid @RequestBody SettleRequest req) {
        return walletService.settleBill(billId, req.energyKwh());
    }

    /** Recent gateway attempts, handy for the demo (shows which worker thread handled each). */
    @GetMapping("/admin/gateway-log")
    @PreAuthorize("hasRole('ADMIN')")
    public List<GatewayTransaction> gatewayLog() {
        return gateway.recentTransactions();
    }

    private long currentUserId(Principal principal) {
        return walletService.userIdForEmail(principal.getName());
    }
}
