package com.example.demo.service;

import java.math.BigDecimal;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.evcharge.wallet.service.WalletService;
import com.example.demo.model.EvChargingSlot;
import com.example.demo.repository.EvChargingSlotRepository;
import com.example.demo.repository.UserRepository;

/**
 * Booking lifecycle: [slot held] --book (pays advance)--> BOOKED --start--> IN_PROGRESS --settle--> COMPLETED,
 * or BOOKED --cancel--> CANCELLED (advance refunded). Each public method is one transaction.
 */
@Service
public class BookingService {

    public record BookingView(long bookingId, long billId, String status, String stationName, String slotNumber,
                              String chargerType, int durationMinutes, BigDecimal advanceAmount,
                              BigDecimal energyKwh, BigDecimal amount, String paymentStatus, LocalDateTime createdAt) {}

    private static final String SELECT_VIEW =
            "SELECT bk.booking_id, b.bill_id, bk.status, st.name AS station_name, s.slot_number, s.charger_type, " +
            "bk.duration_minutes, b.advance_amount, b.energy_kwh, b.amount, b.payment_status, bk.created_at " +
            "FROM bookings bk JOIN bills b ON b.booking_id = bk.booking_id " +
            "JOIN ev_charging_slots s ON s.id = bk.slot_id JOIN ev_charging_stations st ON st.id = s.station_id ";

    private final JdbcTemplate jdbc;
    private final UserRepository users;
    private final EvChargingSlotRepository slots;
    private final WalletService wallets;

    public BookingService(JdbcTemplate jdbc, UserRepository users, EvChargingSlotRepository slots, WalletService wallets) {
        this.jdbc = jdbc;
        this.users = users;
        this.slots = slots;
        this.wallets = wallets;
    }

    /** Confirm the held slot and pay the advance from the wallet. All or nothing: if the balance is short, the hold stays. */
    @Transactional
    public BookingView book(String username, long slotId, int minutes) {
        long userId = userId(username);
        EvChargingSlot slot = slots.findByIdForUpdate(slotId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Slot not found"));
        if (!"SOFT_LOCKED".equals(slot.getStatus()) || !Long.valueOf(userId).equals(slot.getLockedByUserId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "You do not hold an active lock on this slot!");
        }
        if (slot.getLockExpiryTime().isBefore(LocalDateTime.now())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Lock expired! Please try booking again.");
        }
        if (minutes < 15 || minutes > 480) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Duration must be between 15 and 480 minutes");
        }

        long bookingId = insert("INSERT INTO bookings (user_id, slot_id, duration_minutes) VALUES (?, ?, ?)",
                userId, slotId, minutes);
        BigDecimal advance = jdbc.queryForObject("SELECT fn_advance_amount(?, ?)", BigDecimal.class, slotId, minutes);
        long billId = insert("INSERT INTO bills (booking_id, rate_per_kwh, advance_amount) VALUES (?, ?, ?)",
                bookingId, slot.getRatePerKwh(), advance);

        wallets.payAdvance(billId, userId);          // throws InsufficientBalanceException -> everything rolls back

        slot.setStatus("BOOKED");
        slot.setLockedByUserId(null);
        slot.setLockExpiryTime(null);
        return one(bookingId);
    }

    /** Cancel before charging starts: advance refunded in full, slot freed. */
    @Transactional
    public BigDecimal cancel(String username, long bookingId) {
        Object[] row = ownBooking(username, bookingId, "BOOKED");
        return cancelLocked(bookingId, (Long) row[1], "AVAILABLE");
    }

    /** Cancels a BOOKED booking on the system's behalf (cleanup, maintenance). Returns null if it is no longer BOOKED. */
    @Transactional
    public BigDecimal systemCancel(long bookingId) {
        List<Long> slot = jdbc.query("SELECT slot_id FROM bookings WHERE booking_id = ? AND status = 'BOOKED' FOR UPDATE",
                (rs, n) -> rs.getLong(1), bookingId);
        return slot.isEmpty() ? null : cancelLocked(bookingId, slot.get(0), "AVAILABLE");
    }

    /** Booking row must already be locked. Lock order everywhere: booking -> bill -> wallet -> slot. */
    private BigDecimal cancelLocked(long bookingId, long slotId, String slotStatusAfter) {
        jdbc.update("UPDATE bookings SET status = 'CANCELLED', cancelled_at = NOW() WHERE booking_id = ?", bookingId);
        BigDecimal refund = wallets.refundCancelledBooking(bookingId);
        jdbc.update("UPDATE ev_charging_slots SET status = ?, locked_by_user_id = NULL, lock_expiry_time = NULL WHERE id = ?",
                slotStatusAfter, slotId);
        return refund;
    }

    /**
     * Admin: take a slot out of service (a BOOKED booking on it is cancelled and refunded) or put it back.
     * A session already IN_PROGRESS must be settled first.
     */
    @Transactional
    public String setMaintenance(long slotId, boolean on) {
        if (!slots.existsById(slotId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Slot not found");
        }
        if (!on) {
            int n = jdbc.update("UPDATE ev_charging_slots SET status = 'AVAILABLE' WHERE id = ? AND status = 'MAINTENANCE'", slotId);
            return n == 1 ? "Slot is back in service" : "Slot was not under maintenance";
        }
        String note = "Slot is under maintenance";
        List<Object[]> live = jdbc.query(
                "SELECT booking_id, status FROM bookings WHERE slot_id = ? AND status IN ('BOOKED', 'IN_PROGRESS') FOR UPDATE",
                (rs, n) -> new Object[] {rs.getLong(1), rs.getString(2)}, slotId);
        if (!live.isEmpty()) {
            if ("IN_PROGRESS".equals(live.get(0)[1])) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "A charging session is in progress. Settle it first.");
            }
            BigDecimal refund = cancelLocked((Long) live.get(0)[0], slotId, "MAINTENANCE");
            note += "; its booking was cancelled and Rs " + refund + " refunded";
        }
        // atomic: if someone grabbed the slot in the meantime, this changes nothing
        jdbc.update("UPDATE ev_charging_slots SET status = 'MAINTENANCE', locked_by_user_id = NULL, lock_expiry_time = NULL " +
                "WHERE id = ? AND status IN ('AVAILABLE', 'SOFT_LOCKED')", slotId);
        String status = jdbc.queryForObject("SELECT status FROM ev_charging_slots WHERE id = ?", String.class, slotId);
        if (!"MAINTENANCE".equals(status)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Slot just got a new booking. Try again.");
        }
        return note;
    }

    /** Ids of BOOKED bookings that nobody started within the grace period. */
    @Transactional(readOnly = true)
    public List<Long> unstartedOlderThan(int minutes) {
        return jdbc.query("SELECT booking_id FROM bookings WHERE status = 'BOOKED' AND created_at < NOW() - INTERVAL ? MINUTE",
                (rs, n) -> rs.getLong(1), minutes);
    }

    /** Admin revenue report from the SQL views. */
    @Transactional(readOnly = true)
    public List<java.util.Map<String, Object>> revenue(String period) {
        String view = switch (period) {
            case "daily" -> "v_daily_revenue ORDER BY revenue_date DESC LIMIT 31";
            case "weekly" -> "v_weekly_revenue ORDER BY year_week DESC LIMIT 12";
            case "monthly" -> "v_monthly_revenue ORDER BY revenue_month DESC LIMIT 12";
            case "type" -> "v_revenue_by_charger_type";
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "period must be daily, weekly, monthly or type");
        };
        List<java.util.Map<String, Object>> rows = jdbc.queryForList("SELECT * FROM " + view);
        // plain dates as "yyyy-MM-dd" strings; as java.sql.Date they would be JSON-shifted by the timezone to the previous day
        rows.forEach(r -> r.replaceAll((k, v) -> v instanceof java.sql.Date d ? d.toLocalDate().toString() : v));
        return rows;
    }

    /** Unlock the charger. The bill is always PAID by now; the DB trigger enforces it anyway. */
    @Transactional
    public BookingView start(String username, long bookingId) {
        ownBooking(username, bookingId, "BOOKED");
        jdbc.update("UPDATE bookings SET status = 'IN_PROGRESS' WHERE booking_id = ?", bookingId);
        return one(bookingId);
    }

    @Transactional(readOnly = true)
    public List<BookingView> mine(String username) {
        return jdbc.query(SELECT_VIEW + "WHERE bk.user_id = ? ORDER BY bk.booking_id DESC LIMIT 50",
                (rs, n) -> map(rs), userId(username));
    }

    /** Admin: every live booking, so a finished session can be settled. */
    @Transactional(readOnly = true)
    public List<BookingView> live() {
        return jdbc.query(SELECT_VIEW + "WHERE bk.status IN ('BOOKED', 'IN_PROGRESS') ORDER BY bk.booking_id",
                (rs, n) -> map(rs));
    }

    // ---------------------------------------------------------------- helpers
    private BookingView one(long bookingId) {
        return jdbc.queryForObject(SELECT_VIEW + "WHERE bk.booking_id = ?", (rs, n) -> map(rs), bookingId);
    }

    /** Locks the booking row; someone else's booking looks like "not found". Returns {userId, slotId}. */
    private Object[] ownBooking(String username, long bookingId, String requiredStatus) {
        long userId = userId(username);
        List<Object[]> rows = jdbc.query(
                "SELECT user_id, slot_id, status FROM bookings WHERE booking_id = ? FOR UPDATE",
                (rs, n) -> new Object[] {rs.getLong("user_id"), rs.getLong("slot_id"), rs.getString("status")}, bookingId);
        if (rows.isEmpty() || (long) rows.get(0)[0] != userId) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Booking not found");
        }
        if (!requiredStatus.equals(rows.get(0)[2])) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Booking is " + rows.get(0)[2]);
        }
        return rows.get(0);
    }

    private long insert(String sql, Object... args) {
        GeneratedKeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(con -> {
            var ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            for (int i = 0; i < args.length; i++) ps.setObject(i + 1, args[i]);
            return ps;
        }, keys);
        return keys.getKey().longValue();
    }

    private long userId(String username) {
        return users.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"))
                .getId();
    }

    private static BookingView map(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new BookingView(rs.getLong("booking_id"), rs.getLong("bill_id"), rs.getString("status"),
                rs.getString("station_name"), rs.getString("slot_number"), rs.getString("charger_type"),
                rs.getInt("duration_minutes"), rs.getBigDecimal("advance_amount"), rs.getBigDecimal("energy_kwh"),
                rs.getBigDecimal("amount"), rs.getString("payment_status"), rs.getTimestamp("created_at").toLocalDateTime());
    }
}
