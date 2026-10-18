package com.example.demo.controller;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.demo.service.BookingService;
import com.example.demo.service.BookingService.BookingView;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

@RestController
@RequestMapping("/api")
public class BookingController {

    public record BookRequest(@Min(1) long slotId, @Min(15) @Max(480) int durationMinutes) {}

    private final BookingService bookings;

    public BookingController(BookingService bookings) {
        this.bookings = bookings;
    }

    /** Confirm the held slot and pay the advance from the wallet in one step. */
    @PostMapping("/bookings")
    public BookingView book(Authentication auth, @Valid @RequestBody BookRequest req) {
        return bookings.book(auth.getName(), req.slotId(), req.durationMinutes());
    }

    @GetMapping("/bookings/me")
    public List<BookingView> mine(Authentication auth) {
        return bookings.mine(auth.getName());
    }

    @PostMapping("/bookings/{id}/cancel")
    public Map<String, BigDecimal> cancel(Authentication auth, @PathVariable long id) {
        return Map.of("refunded", bookings.cancel(auth.getName(), id));
    }

    @PostMapping("/bookings/{id}/start")
    public BookingView start(Authentication auth, @PathVariable long id) {
        return bookings.start(auth.getName(), id);
    }

    public record MaintenanceRequest(boolean enabled) {}

    @PostMapping("/admin/slots/{id}/maintenance")
    @PreAuthorize("hasRole('ADMIN')")
    public Map<String, String> maintenance(@PathVariable long id, @RequestBody MaintenanceRequest req) {
        return Map.of("message", bookings.setMaintenance(id, req.enabled()));
    }

    @GetMapping("/admin/revenue")
    @PreAuthorize("hasRole('ADMIN')")
    public List<Map<String, Object>> revenue(@org.springframework.web.bind.annotation.RequestParam(defaultValue = "daily") String period) {
        return bookings.revenue(period);
    }

    @GetMapping("/admin/bookings")
    @PreAuthorize("hasRole('ADMIN')")
    public List<BookingView> live() {
        return bookings.live();
    }
}
