package com.example.demo.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Frees slots held by paid bookings nobody started: cancels them and refunds the advance. */
@Component
public class BookingJanitor {

    private static final Logger log = LoggerFactory.getLogger(BookingJanitor.class);

    private final BookingService bookings;
    private final int graceMinutes;

    public BookingJanitor(BookingService bookings, @Value("${booking.unstarted-grace-minutes:120}") int graceMinutes) {
        this.bookings = bookings;
        this.graceMinutes = graceMinutes;
    }

    @Scheduled(fixedDelay = 60_000)
    public void cancelUnstarted() {
        for (long id : bookings.unstartedOlderThan(graceMinutes)) {
            try {                                    // one transaction each; a failure must not block the rest
                if (bookings.systemCancel(id) != null) log.info("Auto-cancelled unstarted booking {}", id);
            } catch (RuntimeException e) {
                log.warn("Could not auto-cancel booking {}", id, e);
            }
        }
    }
}
