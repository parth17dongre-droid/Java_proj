package com.example.demo.controller;

import com.example.demo.dto.EvStationResponseDto;
import com.example.demo.model.EvChargingSlot;
import com.example.demo.service.EvStationService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/ev-stations")
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

    /** Holds the slot for 5 minutes; the user comes from the JWT, never from the request. */
    @PostMapping("/slots/{slotId}/lock")
    public EvChargingSlot lockSlot(@PathVariable Long slotId, Authentication auth) {
        return evStationService.acquireSoftLock(slotId, auth.getName());
    }

    @PostMapping("/slots/{slotId}/release")
    public void releaseSlot(@PathVariable Long slotId, Authentication auth) {
        evStationService.releaseLock(slotId, auth.getName());
    }
}
