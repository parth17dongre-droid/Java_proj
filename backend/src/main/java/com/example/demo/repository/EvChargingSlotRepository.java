package com.example.demo.repository;

import com.example.demo.model.EvChargingSlot;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface EvChargingSlotRepository extends JpaRepository<EvChargingSlot, Long> {

    @Query("SELECT s FROM EvChargingSlot s WHERE s.status = 'SOFT_LOCKED' AND s.lockExpiryTime < :now")
    List<EvChargingSlot> findExpiredSoftLocks(LocalDateTime now);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM EvChargingSlot s WHERE s.id = :id")
    Optional<EvChargingSlot> findByIdForUpdate(Long id);

    /** Atomic check-and-set: of two simultaneous callers exactly one gets 1, the other 0. */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE EvChargingSlot s SET s.status = 'SOFT_LOCKED', s.lockedByUserId = :userId, s.lockExpiryTime = :expiry " +
           "WHERE s.id = :id AND s.status = 'AVAILABLE'")
    int tryLock(Long id, Long userId, LocalDateTime expiry);
}
