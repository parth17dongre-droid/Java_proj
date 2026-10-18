package com.evcharge.wallet.service;

import com.evcharge.wallet.exception.BillStateException;
import com.evcharge.wallet.exception.InsufficientBalanceException;
import com.evcharge.wallet.exception.PaymentException;
import com.evcharge.wallet.exception.ResourceNotFoundException;
import com.evcharge.wallet.model.BillRow;
import com.evcharge.wallet.model.PendingBill;
import com.evcharge.wallet.model.SettlementResult;
import com.evcharge.wallet.model.TxnType;
import com.evcharge.wallet.model.WalletRow;
import com.evcharge.wallet.model.WalletTxn;
import com.evcharge.wallet.repository.BillRepository;
import com.evcharge.wallet.repository.WalletRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * All wallet money movement. Every public method that changes money is ONE database
 * transaction: either every step happens (balance, ledger row, bill status) or none does.
 *
 * Bill lifecycle handled here:
 *   PENDING --payAdvance--> PAID --settleBill--> SETTLED
 *   (a PAID bill whose booking is cancelled goes PAID -> SETTLED through refundCancelledBooking)
 */
@Service
public class WalletService {

    private final WalletRepository wallets;
    private final BillRepository bills;

    public WalletService(WalletRepository wallets, BillRepository bills) {
        this.wallets = wallets;
        this.bills = bills;
    }

    // ------------------------------------------------------------------ wallet basics
    /** Call from registration, in the same transaction that creates the user. */
    @Transactional
    public void createWallet(long userId) {
        try {
            wallets.createWallet(userId);
        } catch (DuplicateKeyException e) {
            throw new PaymentException("Wallet already exists for user " + userId, e);
        }
    }

    @Transactional(readOnly = true)
    public Long userIdForEmail(String email) {
        return wallets.findUserIdByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    @Transactional(readOnly = true)
    public BigDecimal getBalance(long userId) {
        return wallets.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Wallet not found"))
                .balance();
    }

    @Transactional(readOnly = true)
    public List<WalletTxn> getStatement(long userId, int limit) {
        return wallets.findTransactions(userId, Math.max(1, Math.min(limit, 200)));
    }

    @Transactional(readOnly = true)
    public List<PendingBill> getPendingBills(long userId) {
        return bills.findPendingForUser(userId);
    }

    /** Removes an unused wallet (no ledger history). Returns false if it has history. */
    @Transactional
    public boolean deleteUnusedWallet(long userId) {
        return wallets.deleteIfUnused(userId);
    }

    // ------------------------------------------------------------------ top-up
    /** Called by PaymentService AFTER the gateway approved the card/UPI payment. */
    @Transactional
    public BigDecimal creditTopUp(long userId, BigDecimal amount) {
        WalletRow wallet = lockWallet(userId);
        BigDecimal newBalance = wallet.balance().add(amount);
        wallets.updateBalance(wallet.walletId(), newBalance);
        wallets.insertTransaction(wallet.walletId(), null, TxnType.TOPUP, amount, newBalance);
        return newBalance;
    }

    // ------------------------------------------------------------------ pay advance (unlocks charger)
    /**
     * Pays the advance of a bill from the wallet. After this commits, the bill is PAID and
     * the database trigger allows the booking to move to IN_PROGRESS (charger unlocked).
     */
    @Transactional
    public BigDecimal payAdvance(long billId, long userId) {
        // lock order: booking -> bill (inside lockByBillId), then wallet
        BillRow bill = bills.lockByBillId(billId)
                .filter(b -> b.userId() == userId)               // someone else's bill looks like "not found"
                .orElseThrow(() -> new ResourceNotFoundException("Bill not found"));

        if (!"BOOKED".equals(bill.bookingStatus())) {
            throw new BillStateException("Booking is " + bill.bookingStatus() + ", so it cannot be paid");
        }
        if (!"PENDING".equals(bill.paymentStatus())) {
            throw new BillStateException("Bill is already " + bill.paymentStatus());
        }

        WalletRow wallet = lockWallet(userId);
        BigDecimal advance = bill.advanceAmount();
        BigDecimal newBalance = wallet.balance();

        if (advance.signum() > 0) {
            if (wallet.balance().compareTo(advance) < 0) {
                throw new InsufficientBalanceException("Wallet balance Rs " + wallet.balance()
                        + " is less than the advance of Rs " + advance + ". Please add money.");
            }
            newBalance = wallet.balance().subtract(advance);
            wallets.updateBalance(wallet.walletId(), newBalance);
            wallets.insertTransaction(wallet.walletId(), billId, TxnType.ADVANCE_DEBIT, advance, newBalance);
        }
        if (!bills.markPaid(billId)) {
            throw new BillStateException("Bill could not be marked paid");   // rolls everything back
        }
        return newBalance;
    }

    // ------------------------------------------------------------------ settle after the session
    /**
     * Final billing on the energy actually consumed:  final = energy x rate.
     * Unused advance is refunded; if the session cost more than the advance, the extra
     * is debited. Also marks the booking COMPLETED and frees the slot.
     */
    @Transactional
    public SettlementResult settleBill(long billId, BigDecimal energyKwh) {
        BillRow bill = bills.lockByBillId(billId)
                .orElseThrow(() -> new ResourceNotFoundException("Bill not found"));

        if (!"PAID".equals(bill.paymentStatus())) {
            throw new BillStateException("Only a PAID bill can be settled; this one is " + bill.paymentStatus());
        }
        if (!"BOOKED".equals(bill.bookingStatus()) && !"IN_PROGRESS".equals(bill.bookingStatus())) {
            throw new BillStateException("Booking is " + bill.bookingStatus());
        }

        BigDecimal energy = energyKwh.setScale(3, RoundingMode.HALF_UP);              // DECIMAL(8,3)
        BigDecimal finalAmount = energy.multiply(bill.ratePerKwh()).setScale(2, RoundingMode.HALF_UP);
        BigDecimal difference = bill.advanceAmount().subtract(finalAmount);          // + refund, - extra

        WalletRow wallet = lockWallet(bill.userId());
        BigDecimal newBalance = wallet.balance();
        BigDecimal refunded = BigDecimal.ZERO.setScale(2);
        BigDecimal extra = BigDecimal.ZERO.setScale(2);

        if (difference.signum() > 0) {
            refunded = difference;
            newBalance = newBalance.add(refunded);
            wallets.updateBalance(wallet.walletId(), newBalance);
            wallets.insertTransaction(wallet.walletId(), billId, TxnType.REFUND, refunded, newBalance);
        } else if (difference.signum() < 0) {
            extra = difference.negate();
            if (newBalance.compareTo(extra) < 0) {
                throw new InsufficientBalanceException("Wallet cannot cover the extra Rs " + extra);
            }
            newBalance = newBalance.subtract(extra);
            wallets.updateBalance(wallet.walletId(), newBalance);
            wallets.insertTransaction(wallet.walletId(), billId, TxnType.FINAL_DEBIT, extra, newBalance);
        }

        if (!bills.markSettled(billId, energy, finalAmount)) {
            throw new BillStateException("Bill could not be settled");
        }
        bills.markBookingCompleted(bill.bookingId());
        bills.releaseSlot(bill.bookingId());
        return new SettlementResult(finalAmount, refunded, extra, newBalance);
    }

    // ------------------------------------------------------------------ cancellation
    /**
     * Call this from the booking-cancel code, in the SAME transaction, after the booking
     * status has been set to CANCELLED. A paid advance is refunded in full and the bill is
     * settled at 0 (the revenue views ignore it because the booking is not COMPLETED).
     * Unpaid bills need nothing. Returns the amount refunded.
     */
    @Transactional
    public BigDecimal refundCancelledBooking(long bookingId) {
        BillRow bill = bills.lockByBookingId(bookingId).orElse(null);
        if (bill == null || !"PAID".equals(bill.paymentStatus())) {
            return BigDecimal.ZERO.setScale(2);
        }
        if (!"CANCELLED".equals(bill.bookingStatus())) {
            throw new BillStateException("Booking " + bookingId + " is not cancelled");
        }
        WalletRow wallet = lockWallet(bill.userId());
        BigDecimal refund = bill.advanceAmount();
        BigDecimal newBalance = wallet.balance().add(refund);
        if (refund.signum() > 0) {
            wallets.updateBalance(wallet.walletId(), newBalance);
            wallets.insertTransaction(wallet.walletId(), bill.billId(), TxnType.REFUND, refund, newBalance);
        }
        bills.markSettled(bill.billId(), new BigDecimal("0.000"), new BigDecimal("0.00"));
        return refund;
    }

    // ------------------------------------------------------------------ helpers
    private WalletRow lockWallet(long userId) {
        return wallets.findByUserIdForUpdate(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Wallet not found"));
    }
}
