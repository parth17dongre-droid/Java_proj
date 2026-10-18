package com.evcharge.wallet.repository;

import com.evcharge.wallet.model.BillRow;
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
                "UPDATE ev_charging_slots s JOIN bookings bk ON bk.slot_id = s.id " +
                        "SET s.status = 'AVAILABLE', s.locked_by_user_id = NULL, s.lock_expiry_time = NULL WHERE bk.booking_id = ?",
                bookingId
        );
    }
}