package com.evcharge.wallet.repository;

import com.evcharge.wallet.model.WalletRow;
import com.evcharge.wallet.model.WalletTxn;
import com.evcharge.wallet.model.TxnType;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class WalletRepository {

    private final JdbcTemplate jdbc;

    public WalletRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void createWallet(long userId) {
        jdbc.update("INSERT INTO wallets (user_id, balance, version) VALUES (?, 0.00, 0)", userId);
    }

    public Optional<Long> findUserIdByUsername(String username) {
        List<Long> ids = jdbc.query("SELECT id FROM users WHERE username = ?", (rs, rowNum) -> rs.getLong("id"), username);
        return ids.isEmpty() ? Optional.empty() : Optional.of(ids.get(0));
    }

    public Optional<WalletRow> findByUserId(long userId) {
        List<WalletRow> rows = jdbc.query(
                "SELECT wallet_id, user_id, balance, version FROM wallets WHERE user_id = ?",
                (rs, rowNum) -> new WalletRow(rs.getLong("wallet_id"), rs.getLong("user_id"), rs.getBigDecimal("balance"), rs.getLong("version")),
                userId
        );
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    public Optional<WalletRow> findByUserIdForUpdate(long userId) {
        List<WalletRow> rows = jdbc.query(
                "SELECT wallet_id, user_id, balance, version FROM wallets WHERE user_id = ? FOR UPDATE",
                (rs, rowNum) -> new WalletRow(rs.getLong("wallet_id"), rs.getLong("user_id"), rs.getBigDecimal("balance"), rs.getLong("version")),
                userId
        );
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    public void updateBalance(long walletId, BigDecimal newBalance) {
        jdbc.update("UPDATE wallets SET balance = ?, version = version + 1 WHERE wallet_id = ?", newBalance, walletId);
    }

    public void insertTransaction(long walletId, Long billId, TxnType type, BigDecimal amount, BigDecimal balanceAfter) {
        jdbc.update(
                "INSERT INTO wallet_transactions (wallet_id, bill_id, type, amount, balance_after) VALUES (?, ?, ?, ?, ?)",
                walletId, billId, type.name(), amount, balanceAfter
        );
    }

    public List<WalletTxn> findTransactions(long userId, int limit) {
        return jdbc.query(
                "SELECT t.txn_id, t.wallet_id, t.bill_id, t.type, t.amount, t.balance_after, t.created_at " +
                        "FROM wallet_transactions t JOIN wallets w ON w.wallet_id = t.wallet_id " +
                        "WHERE w.user_id = ? ORDER BY t.txn_id DESC LIMIT ?",
                (rs, rowNum) -> new WalletTxn(
                        rs.getLong("txn_id"),
                        rs.getLong("wallet_id"),
                        rs.getObject("bill_id") != null ? rs.getLong("bill_id") : null,
                        rs.getString("type"),
                        rs.getBigDecimal("amount"),
                        rs.getBigDecimal("balance_after"),
                        rs.getTimestamp("created_at").toLocalDateTime()
                ),
                userId, limit
        );
    }

    public boolean deleteIfUnused(long userId) {
        // Only delete if zero transactions exist
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM wallet_transactions t JOIN wallets w ON w.wallet_id = t.wallet_id WHERE w.user_id = ?",
                Integer.class, userId
        );
        if (count != null && count == 0) {
            jdbc.update("DELETE FROM wallets WHERE user_id = ?", userId);
            return true;
        }
        return false;
    }
}