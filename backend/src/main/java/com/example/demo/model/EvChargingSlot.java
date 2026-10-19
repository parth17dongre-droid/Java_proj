package com.example.demo.model;

import jakarta.persistence.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
import java.time.LocalDateTime;

@Entity
@Table(name = "ev_charging_slots")
public class EvChargingSlot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String slotNumber; 
    private String connectorType; // matches standard names like CCS2, TYPE2
    private String status = "AVAILABLE"; // AVAILABLE, SOFT_LOCKED, BOOKED

    private Long lockedByUserId;
    private LocalDateTime lockExpiryTime;

    @ManyToOne
    @JoinColumn(name = "station_id")
    @JsonIgnore
    private EvChargingStation station;

    public EvChargingSlot() {}

    public Long getId() { return id; }
    public String getSlotNumber() { return slotNumber; }
    public String getConnectorType() { return connectorType; }
    public String getStatus() { return status; }
    public Long getLockedByUserId() { return lockedByUserId; }
    public LocalDateTime getLockExpiryTime() { return lockExpiryTime; }
    public EvChargingStation getStation() { return station; }

    public void setId(Long id) { this.id = id; }
    public void setSlotNumber(String slotNumber) { this.slotNumber = slotNumber; }
    public void setConnectorType(String connectorType) { this.connectorType = connectorType; }
    public void setStatus(String status) { this.status = status; }
    public void setLockedByUserId(Long lockedByUserId) { this.lockedByUserId = lockedByUserId; }
    public void setLockExpiryTime(LocalDateTime lockExpiryTime) { this.lockExpiryTime = lockExpiryTime; }
    public void setStation(EvChargingStation station) { this.station = station; }
}