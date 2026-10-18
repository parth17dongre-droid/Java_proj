package com.example.demo.service;

import com.example.demo.dto.EvStationResponseDto;
import com.example.demo.model.EvChargingSlot;
import com.example.demo.model.EvChargingStation;
import com.example.demo.repository.EvChargingSlotRepository;
import com.example.demo.repository.EvChargingStationRepository;
import com.example.demo.utils.GeoDistanceUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class EvStationService {

    @Autowired
    private EvChargingStationRepository stationRepository;

    @Autowired
    private EvChargingSlotRepository slotRepository;

    public List<EvStationResponseDto> getNearbyStations(double userLat, double userLng) {
        List<EvChargingStation> stations = stationRepository.findAll();

        return stations.stream().map(station -> {
            double distance = GeoDistanceUtil.calculateDistance(
                    userLat, userLng,
                    station.getLatitude(), station.getLongitude()
            );
            return new EvStationResponseDto(
                    station.getId(),
                    station.getName(),
                    station.getAddress(),
                    station.getLatitude(),
                    station.getLongitude(),
                    distance,
                    station.getSlots()
            );
        })
        .sorted(Comparator.comparingDouble(EvStationResponseDto::getDistanceKm))
        .collect(Collectors.toList());
    }

    @Transactional
    public EvChargingSlot acquireSoftLock(Long slotId, Long userId) {
        EvChargingSlot slot = slotRepository.findById(slotId)
                .orElseThrow(() -> new RuntimeException("Slot not found"));

        if (!"AVAILABLE".equals(slot.getStatus())) {
            throw new RuntimeException("Slot is no longer available for booking!");
        }

        slot.setStatus("SOFT_LOCKED");
        slot.setLockedByUserId(userId);
        slot.setLockExpiryTime(LocalDateTime.now().plusMinutes(5));

        return slotRepository.save(slot);
    }

    @Transactional
    public EvChargingSlot confirmBooking(Long slotId, Long userId) {
        EvChargingSlot slot = slotRepository.findById(slotId)
                .orElseThrow(() -> new RuntimeException("Slot not found"));

        if (!"SOFT_LOCKED".equals(slot.getStatus()) || !userId.equals(slot.getLockedByUserId())) {
            throw new RuntimeException("You do not hold an active lock on this slot!");
        }

        if (slot.getLockExpiryTime().isBefore(LocalDateTime.now())) {
            slot.setStatus("AVAILABLE");
            slot.setLockedByUserId(null);
            slot.setLockExpiryTime(null);
            slotRepository.save(slot);
            throw new RuntimeException("Lock expired! Please try booking again.");
        }

        slot.setStatus("BOOKED");
        slot.setLockExpiryTime(null);

        return slotRepository.save(slot);
    }

    @Transactional
    public void releaseLock(Long slotId, Long userId) {
        EvChargingSlot slot = slotRepository.findById(slotId).orElse(null);
        if (slot != null && "SOFT_LOCKED".equals(slot.getStatus()) && userId.equals(slot.getLockedByUserId())) {
            slot.setStatus("AVAILABLE");
            slot.setLockedByUserId(null);
            slot.setLockExpiryTime(null);
            slotRepository.save(slot);
        }
    }

    @Scheduled(fixedRate = 10000)
    @Transactional
    public void autoReleaseExpiredLocks() {
        List<EvChargingSlot> expiredSlots = slotRepository.findExpiredSoftLocks(LocalDateTime.now());
        for (EvChargingSlot slot : expiredSlots) {
            slot.setStatus("AVAILABLE");
            slot.setLockedByUserId(null);
            slot.setLockExpiryTime(null);
            slotRepository.save(slot);
        }
    }
}