package com.example.demo.model;

import jakarta.persistence.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
import java.math.BigDecimal;
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

    @Column(columnDefinition = "VARCHAR(10) NOT NULL DEFAULT 'SLOW'")
    private String chargerType = "SLOW"; // FAST or SLOW

    @Column(columnDefinition = "DECIMAL(6,2) NOT NULL DEFAULT 7.40")
    private BigDecimal powerKw = new BigDecimal("7.40");

    @Column(columnDefinition = "DECIMAL(8,2) NOT NULL DEFAULT 10.00")
    private BigDecimal ratePerKwh = new BigDecimal("10.00");

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
    public String getChargerType() { return chargerType; }
    public BigDecimal getPowerKw() { return powerKw; }
    public BigDecimal getRatePerKwh() { return ratePerKwh; }
    public Long getLockedByUserId() { return lockedByUserId; }
    public LocalDateTime getLockExpiryTime() { return lockExpiryTime; }
    public EvChargingStation getStation() { return station; }

    public void setId(Long id) { this.id = id; }
    public void setSlotNumber(String slotNumber) { this.slotNumber = slotNumber; }
    public void setConnectorType(String connectorType) { this.connectorType = connectorType; }
    public void setStatus(String status) { this.status = status; }
    public void setChargerType(String chargerType) { this.chargerType = chargerType; }
    public void setPowerKw(BigDecimal powerKw) { this.powerKw = powerKw; }
    public void setRatePerKwh(BigDecimal ratePerKwh) { this.ratePerKwh = ratePerKwh; }
    public void setLockedByUserId(Long lockedByUserId) { this.lockedByUserId = lockedByUserId; }
    public void setLockExpiryTime(LocalDateTime lockExpiryTime) { this.lockExpiryTime = lockExpiryTime; }
    public void setStation(EvChargingStation station) { this.station = station; }
}