package com.evcharge.wallet.repository;

import com.evcharge.wallet.model.BillRow;
import com.evcharge.wallet.model.PendingBill;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class BillRepository {

    private final JdbcTemplate jdbc;

    public BillRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<BillRow> lockByBillId(long billId) {
        List<BillRow> rows = jdbc.query(
                "SELECT b.bill_id, b.booking_id, bk.user_id, b.rate_per_kwh, b.advance_amount, b.payment_status, bk.status AS booking_status " +
                        "FROM bills b JOIN bookings bk ON bk.booking_id = b.booking_id " +
                        "WHERE b.bill_id = ? FOR UPDATE",
                (rs, rowNum) -> new BillRow(
                        rs.getLong("bill_id"),
                        rs.getLong("booking_id"),
                        rs.getLong("user_id"),
                        rs.getBigDecimal("rate_per_kwh"),
                        rs.getBigDecimal("advance_amount"),
                        rs.getString("payment_status"),
                        rs.getString("booking_status")
                ),
                billId
        );
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    public Optional<BillRow> lockByBookingId(long bookingId) {
        List<BillRow> rows = jdbc.query(
                "SELECT b.bill_id, b.booking_id, bk.user_id, b.rate_per_kwh, b.advance_amount, b.payment_status, bk.status AS booking_status " +
                        "FROM bills b JOIN bookings bk ON bk.booking_id = b.booking_id " +
                        "WHERE b.booking_id = ? FOR UPDATE",
                (rs, rowNum) -> new BillRow(
                        rs.getLong("bill_id"),
                        rs.getLong("booking_id"),
                        rs.getLong("user_id"),
                        rs.getBigDecimal("rate_per_kwh"),
                        rs.getBigDecimal("advance_amount"),
                        rs.getString("payment_status"),
                        rs.getString("booking_status")
                ),
                bookingId
        );
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    public List<PendingBill> findPendingForUser(long userId) {
        return jdbc.query(
                "SELECT b.bill_id, c.charger_code, c.type AS charger_type, s.start_time, b.advance_amount " +
                        "FROM bills b " +
                        "JOIN bookings bk ON bk.booking_id = b.booking_id " +
                        "JOIN slots s ON s.slot_id = bk.slot_id " +
                        "JOIN chargers c ON c.charger_id = s.charger_id " +
                        "WHERE bk.user_id = ? AND b.payment_status = 'PENDING' AND bk.status = 'BOOKED' " +
                        "ORDER BY s.start_time ASC",
                (rs, rowNum) -> new PendingBill(
                        rs.getLong("bill_id"),
                        rs.getString("charger_code"),
                        rs.getString("charger_type"),
                        rs.getTimestamp("start_time").toLocalDateTime(),
                        rs.getBigDecimal("advance_amount")
                ),
                userId
        );
    }

    public boolean markPaid(long billId) {
        return jdbc.update("UPDATE bills SET payment_status = 'PAID', paid_at = NOW() WHERE bill_id = ? AND payment_status = 'PENDING'", billId) > 0;
    }

    public boolean markSettled(long billId, BigDecimal energyKwh, BigDecimal amount) {
        return jdbc.update(
                "UPDATE bills SET payment_status = 'SETTLED', energy_kwh = ?, amount = ?, settled_at = NOW() WHERE bill_id = ?",
                energyKwh, amount, billId
        ) > 0;
    }

    public void markBookingCompleted(long bookingId) {
        jdbc.update("UPDATE bookings SET status = 'COMPLETED' WHERE booking_id = ?", bookingId);
    }

    public void releaseSlot(long bookingId) {
        jdbc.update(
                "UPDATE slots s JOIN bookings bk ON bk.slot_id = s.slot_id SET s.status = 'AVAILABLE' WHERE bk.booking_id = ?",
                bookingId
        );
    }
}