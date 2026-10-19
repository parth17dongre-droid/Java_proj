package com.example.demo.controller;

import com.example.demo.dto.EvStationResponseDto;
import com.example.demo.model.EvChargingSlot;
import com.example.demo.service.EvStationService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/ev-stations")
@CrossOrigin(origins = "*")
public class EvStationController {

    private final EvStationService evStationService;

    public EvStationController(EvStationService evStationService) {
        this.evStationService = evStationService;
    }

    @GetMapping("/nearby")
    public List<EvStationResponseDto> getNearbyStations(
            @RequestParam double lat,
            @RequestParam double lng) {
        return evStationService.getNearbyStations(lat, lng);
    }

    @PostMapping("/slots/{slotId}/lock")
    public EvChargingSlot lockSlot(@PathVariable Long slotId, @RequestParam Long userId) {
        return evStationService.acquireSoftLock(slotId, userId);
    }

    @PostMapping("/slots/{slotId}/confirm")
    public EvChargingSlot confirmSlot(@PathVariable Long slotId, @RequestParam Long userId) {
        return evStationService.confirmBooking(slotId, userId);
    }

    @PostMapping("/slots/{slotId}/release")
    public void releaseSlot(@PathVariable Long slotId, @RequestParam Long userId) {
        evStationService.releaseLock(slotId, userId);
    }
}