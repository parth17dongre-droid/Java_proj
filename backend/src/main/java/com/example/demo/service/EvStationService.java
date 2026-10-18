package com.example.demo.service;

import com.example.demo.dto.EvStationResponseDto;
import com.example.demo.model.EvChargingSlot;
import com.example.demo.model.EvChargingStation;
import com.example.demo.repository.EvChargingSlotRepository;
import com.example.demo.repository.EvChargingStationRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.utils.GeoDistanceUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

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

    @Autowired
    private UserRepository userRepository;

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
    public EvChargingSlot acquireSoftLock(Long slotId, String username) {
        Long userId = userId(username);
        if (!slotRepository.existsById(slotId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Slot not found");
        }
        if (slotRepository.tryLock(slotId, userId, LocalDateTime.now().plusMinutes(5)) == 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Slot is no longer available for booking!");
        }
        return slotRepository.findById(slotId).orElseThrow();
    }

    @Transactional
    public void releaseLock(Long slotId, String username) {
        Long userId = userId(username);
        EvChargingSlot slot = slotRepository.findByIdForUpdate(slotId).orElse(null);
        if (slot != null && "SOFT_LOCKED".equals(slot.getStatus()) && userId.equals(slot.getLockedByUserId())) {
            slot.setStatus("AVAILABLE");
            slot.setLockedByUserId(null);
            slot.setLockExpiryTime(null);
        }
    }

    private Long userId(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"))
                .getId();
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