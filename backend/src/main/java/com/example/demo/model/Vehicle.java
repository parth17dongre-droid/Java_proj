package com.example.demo.model;

import jakarta.persistence.*;

import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "vehicles")
public class Vehicle {

    public enum ConnectorType {
        TYPE_1,          // J1772 (AC)
        TYPE_2,          // Mennekes (AC)
        CCS1,
        CCS2,
        CHADEMO,
        GB_T,
        BHARAT_AC_001,   // India AC
        BHARAT_DC_001,   // India DC
        TESLA
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private Users owner;

    private String make;
    private String model;
    private Integer year;
    private Double batteryCapacityKwh;
    private Double maxChargingPowerKw;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "vehicle_connectors", joinColumns = @JoinColumn(name = "vehicle_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "connector_type")
    private Set<ConnectorType> connectorTypes = new HashSet<>();

    public Vehicle() {
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Users getOwner() { return owner; }
    public void setOwner(Users owner) { this.owner = owner; }

    public String getMake() { return make; }
    public void setMake(String make) { this.make = make; }

    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }

    public Integer getYear() { return year; }
    public void setYear(Integer year) { this.year = year; }

    public Double getBatteryCapacityKwh() { return batteryCapacityKwh; }
    public void setBatteryCapacityKwh(Double batteryCapacityKwh) { this.batteryCapacityKwh = batteryCapacityKwh; }

    public Double getMaxChargingPowerKw() { return maxChargingPowerKw; }
    public void setMaxChargingPowerKw(Double maxChargingPowerKw) { this.maxChargingPowerKw = maxChargingPowerKw; }

    public Set<ConnectorType> getConnectorTypes() { return connectorTypes; }
    public void setConnectorTypes(Set<ConnectorType> connectorTypes) { this.connectorTypes = connectorTypes; }
}