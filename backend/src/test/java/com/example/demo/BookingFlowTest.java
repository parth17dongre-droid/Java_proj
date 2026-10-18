package com.example.demo;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;

import com.evcharge.wallet.exception.InsufficientBalanceException;
import com.evcharge.wallet.model.SettlementResult;
import com.evcharge.wallet.service.WalletService;
import com.example.demo.model.EvChargingSlot;
import com.example.demo.model.Users;
import com.example.demo.repository.EvChargingSlotRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.service.BookingJanitor;
import com.example.demo.service.BookingService;
import com.example.demo.service.BookingService.BookingView;
import com.example.demo.service.EvStationService;

/**
 * Money and booking rules against a real MySQL. Uses throw-away users, but writes to the configured database,
 * so point it at a dev DB:  EV_DB_TEST=true ./mvnw test -Dtest=BookingFlowTest
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "EV_DB_TEST", matches = "true")
class BookingFlowTest {

    @Autowired BookingService bookings;
    @Autowired BookingJanitor janitor;
    @Autowired EvStationService stations;
    @Autowired WalletService wallets;
    @Autowired UserRepository users;
    @Autowired EvChargingSlotRepository slots;
    @Autowired JdbcTemplate jdbc;

    // ---------------------------------------------------------------- helpers
    private String newUser(String money) {
        String name = "t_" + UUID.randomUUID().toString().substring(0, 8);
        Users u = users.save(new Users("Test " + name, name, name + "@test.local", "x"));
        wallets.createWallet(u.getId());
        if (money != null) wallets.creditTopUp(u.getId(), new BigDecimal(money));
        return name;
    }

    private long balance(String username) {
        return wallets.getBalance(wallets.userIdForUsername(username)).longValueExact();
    }

    private BigDecimal balanceExact(String username) {
        return wallets.getBalance(wallets.userIdForUsername(username));
    }

    /** First slot that is free; tests free their slot when done. */
    private EvChargingSlot freeSlot(int skip) {
        return slots.findAll().stream().filter(s -> "AVAILABLE".equals(s.getStatus())).skip(skip).findFirst().orElseThrow();
    }

    private BigDecimal advance(EvChargingSlot s, int minutes) {
        return s.getPowerKw().multiply(new BigDecimal(minutes)).multiply(s.getRatePerKwh())
                .divide(new BigDecimal(60), 2, java.math.RoundingMode.HALF_UP);
    }

    private BookingView holdAndBook(String user, EvChargingSlot slot, int minutes) {
        stations.acquireSoftLock(slot.getId(), user);
        return bookings.book(user, slot.getId(), minutes);
    }

    private String slotStatus(long id) {
        return jdbc.queryForObject("SELECT status FROM ev_charging_slots WHERE id = ?", String.class, id);
    }

    private void assertLedgerConsistent() {
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM v_wallet_reconciliation WHERE is_consistent = 0", Integer.class));
    }

    // ---------------------------------------------------------------- tests
    @Test
    void insufficientBalanceRollsBackAndKeepsTheHold() {
        String u = newUser(null);
        EvChargingSlot slot = freeSlot(0);
        stations.acquireSoftLock(slot.getId(), u);

        assertThrows(InsufficientBalanceException.class, () -> bookings.book(u, slot.getId(), 60));

        assertEquals("SOFT_LOCKED", slotStatus(slot.getId()));
        assertTrue(bookings.mine(u).isEmpty(), "no booking row may survive the rollback");
        assertEquals(0, balance(u));
        stations.releaseLock(slot.getId(), u);
        assertEquals("AVAILABLE", slotStatus(slot.getId()));
    }

    @Test
    void bookStartSettleRefundsUnusedAdvance() {
        String admin = null;
        String u = newUser("2000");
        EvChargingSlot slot = freeSlot(0);
        BigDecimal adv = advance(slot, 60);

        BookingView b = holdAndBook(u, slot, 60);
        assertEquals("BOOKED", b.status());
        assertEquals("PAID", b.paymentStatus());
        assertEquals(0, adv.compareTo(b.advanceAmount()));
        assertEquals(0, new BigDecimal("2000").subtract(adv).compareTo(balanceExact(u)));
        assertEquals("BOOKED", slotStatus(slot.getId()));

        assertEquals("IN_PROGRESS", bookings.start(u, b.bookingId()).status());

        BigDecimal energy = new BigDecimal("5.000");
        BigDecimal cost = energy.multiply(slot.getRatePerKwh());
        SettlementResult r = wallets.settleBill(b.billId(), energy);
        assertEquals(0, cost.compareTo(r.finalAmount()));
        assertEquals(0, adv.subtract(cost).compareTo(r.refunded()));
        assertEquals(0, r.shortfall().signum());
        assertEquals(0, new BigDecimal("2000").subtract(cost).compareTo(balanceExact(u)));
        assertEquals("AVAILABLE", slotStatus(slot.getId()));
        assertEquals("COMPLETED", bookings.mine(u).get(0).status());

        // revenue report sees the settled session
        assertFalse(bookings.revenue("daily").isEmpty());
        assertTrue(bookings.revenue("daily").get(0).get("revenue_date") instanceof String, "dates must not be timezone-shifted");
        assertTrue(bookings.revenue("weekly").get(0).get("week_start") instanceof String);
        assertFalse(bookings.revenue("type").isEmpty());
        assertEquals(HttpStatusCheck.BAD_REQUEST, statusOf(() -> bookings.revenue("yearly")));
        assertLedgerConsistent();
    }

    @Test
    void settleWithShortWalletCollectsWhatItCanAndReportsShortfall() {
        EvChargingSlot slot = freeSlot(0);
        BigDecimal adv = advance(slot, 60);
        String u = newUser(adv.toPlainString());                       // wallet holds exactly the advance
        BookingView b = holdAndBook(u, slot, 60);
        assertEquals(0, balanceExact(u).signum());

        BigDecimal energy = slot.getPowerKw().multiply(new BigDecimal("2"));   // used double the booked energy
        SettlementResult r = wallets.settleBill(b.billId(), energy);

        assertEquals(0, adv.multiply(new BigDecimal("2")).compareTo(r.finalAmount()));
        assertEquals(0, r.extra().signum());                           // nothing left to take
        assertEquals(0, adv.compareTo(r.shortfall()));
        assertEquals(0, balanceExact(u).signum());                     // never negative
        BookingView done = bookings.mine(u).get(0);
        assertEquals("COMPLETED", done.status());
        assertEquals(0, adv.compareTo(done.amount()), "bill records only what was collected");
        assertLedgerConsistent();
    }

    @Test
    void settleChargesExtraWhenWalletCoversIt() {
        EvChargingSlot slot = freeSlot(0);
        BigDecimal adv = advance(slot, 60);
        String u = newUser(adv.add(new BigDecimal("1000")).toPlainString());
        BookingView b = holdAndBook(u, slot, 60);
        SettlementResult r = wallets.settleBill(b.billId(), slot.getPowerKw().multiply(new BigDecimal("1.5")));
        assertEquals(0, adv.divide(new BigDecimal("2"), 2, java.math.RoundingMode.HALF_UP).compareTo(r.extra()));
        assertEquals(0, r.shortfall().signum());
        assertLedgerConsistent();
    }

    @Test
    void cancelRefundsInFullAndFreesTheSlot() {
        String u = newUser("1000");
        EvChargingSlot slot = freeSlot(0);
        BookingView b = holdAndBook(u, slot, 90);

        assertEquals(0, advance(slot, 90).compareTo(bookings.cancel(u, b.bookingId())));
        assertEquals(1000, balance(u));
        assertEquals("AVAILABLE", slotStatus(slot.getId()));
        assertEquals(HttpStatusCheck.CONFLICT, statusOf(() -> bookings.cancel(u, b.bookingId())));
        assertLedgerConsistent();
    }

    @Test
    void otherUsersCannotTouchMyBooking() {
        String owner = newUser("1000");
        String other = newUser("1000");
        EvChargingSlot slot = freeSlot(0);
        BookingView b = holdAndBook(owner, slot, 60);

        assertEquals(HttpStatusCheck.NOT_FOUND, statusOf(() -> bookings.cancel(other, b.bookingId())));
        assertEquals(HttpStatusCheck.NOT_FOUND, statusOf(() -> bookings.start(other, b.bookingId())));
        assertEquals(HttpStatusCheck.CONFLICT, statusOf(() -> bookings.book(other, slot.getId(), 60)));
        bookings.cancel(owner, b.bookingId());
    }

    @Test
    void maintenanceCancelsTheBookingRefundsAndBlocksNewHolds() {
        String u = newUser("1000");
        EvChargingSlot slot = freeSlot(0);
        holdAndBook(u, slot, 60);

        String note = bookings.setMaintenance(slot.getId(), true);
        assertTrue(note.contains("refunded"), note);
        assertEquals(1000, balance(u));
        assertEquals("CANCELLED", bookings.mine(u).get(0).status());
        assertEquals("MAINTENANCE", slotStatus(slot.getId()));
        assertEquals(HttpStatusCheck.CONFLICT, statusOf(() -> stations.acquireSoftLock(slot.getId(), u)));

        bookings.setMaintenance(slot.getId(), false);
        assertEquals("AVAILABLE", slotStatus(slot.getId()));
        assertLedgerConsistent();
    }

    @Test
    void maintenanceIsRefusedWhileAChargingSessionIsRunning() {
        String u = newUser("1000");
        EvChargingSlot slot = freeSlot(0);
        BookingView b = holdAndBook(u, slot, 60);
        bookings.start(u, b.bookingId());

        assertEquals(HttpStatusCheck.CONFLICT, statusOf(() -> bookings.setMaintenance(slot.getId(), true)));
        assertEquals("BOOKED", slotStatus(slot.getId()));
        wallets.settleBill(b.billId(), BigDecimal.ONE);
    }

    @Test
    void janitorCancelsAndRefundsBookingsNobodyStarted() {
        String u = newUser("1000");
        EvChargingSlot slot = freeSlot(0);
        BookingView fresh = holdAndBook(u, slot, 60);
        janitor.cancelUnstarted();
        assertEquals("BOOKED", bookings.mine(u).get(0).status(), "a fresh booking must be left alone");

        jdbc.update("UPDATE bookings SET created_at = NOW() - INTERVAL 5 HOUR WHERE booking_id = ?", fresh.bookingId());
        janitor.cancelUnstarted();

        assertEquals("CANCELLED", bookings.mine(u).get(0).status());
        assertEquals(1000, balance(u));
        assertEquals("AVAILABLE", slotStatus(slot.getId()));
        assertLedgerConsistent();
    }

    @Test
    void janitorLeavesStartedSessionsAlone() {
        String u = newUser("1000");
        EvChargingSlot slot = freeSlot(0);
        BookingView b = holdAndBook(u, slot, 60);
        bookings.start(u, b.bookingId());
        jdbc.update("UPDATE bookings SET created_at = NOW() - INTERVAL 5 HOUR WHERE booking_id = ?", b.bookingId());

        janitor.cancelUnstarted();

        assertEquals("IN_PROGRESS", bookings.mine(u).get(0).status());
        wallets.settleBill(b.billId(), BigDecimal.ONE);
    }

    @Test
    void parallelBookingsCannotSpendTheSameMoneyTwice() throws Exception {
        EvChargingSlot a = freeSlot(0);
        EvChargingSlot c = freeSlot(1);
        String u = newUser(advance(a, 60).toPlainString());            // enough for ONE of the two
        stations.acquireSoftLock(a.getId(), u);
        stations.acquireSoftLock(c.getId(), u);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<Boolean>> results = List.of(a, c).stream().map(s -> pool.submit(() -> {
            go.await();
            try {
                bookings.book(u, s.getId(), 60);
                return true;
            } catch (InsufficientBalanceException e) {
                return false;
            }
        })).toList();
        go.countDown();
        long wins = 0;
        for (Future<Boolean> f : results) if (f.get()) wins++;
        pool.shutdown();

        assertEquals(1, wins, "exactly one booking may be paid for");
        assertTrue(balanceExact(u).signum() >= 0);
        assertEquals(1, bookings.mine(u).size());
        // tidy up: cancel the winner, release the loser's hold
        bookings.cancel(u, bookings.mine(u).get(0).bookingId());
        stations.releaseLock(a.getId(), u);
        stations.releaseLock(c.getId(), u);
        assertLedgerConsistent();
    }

    // ---------------------------------------------------------------- tiny status helper
    enum HttpStatusCheck { BAD_REQUEST, NOT_FOUND, CONFLICT, OTHER }

    private static HttpStatusCheck statusOf(Runnable r) {
        try {
            r.run();
        } catch (ResponseStatusException e) {
            return switch (e.getStatusCode().value()) {
                case 400 -> HttpStatusCheck.BAD_REQUEST;
                case 404 -> HttpStatusCheck.NOT_FOUND;
                case 409 -> HttpStatusCheck.CONFLICT;
                default -> HttpStatusCheck.OTHER;
            };
        }
        fail("expected a ResponseStatusException");
        return null;
    }
}
