package com.example.demo.repository;

import com.example.demo.model.EvChargingSlot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.time.LocalDateTime;
import java.util.List;

public interface EvChargingSlotRepository extends JpaRepository<EvChargingSlot, Long> {
    
    @Query("SELECT s FROM EvChargingSlot s WHERE s.status = 'SOFT_LOCKED' AND s.lockExpiryTime < :now")
    List<EvChargingSlot> findExpiredSoftLocks(LocalDateTime now);
}