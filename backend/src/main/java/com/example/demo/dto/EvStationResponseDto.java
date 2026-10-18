package com.example.demo.dto;

import com.example.demo.model.EvChargingSlot;
import java.util.List;

public class EvStationResponseDto {
    private Long id;
    private String name;
    private String address;
    private Double latitude;
    private Double longitude;
    private Double distanceKm;
    private List<EvChargingSlot> slots;

    public EvStationResponseDto(Long id, String name, String address, Double latitude, Double longitude, Double distanceKm, List<EvChargingSlot> slots) {
        this.id = id;
        this.name = name;
        this.address = address;
        this.latitude = latitude;
        this.longitude = longitude;
        this.distanceKm = distanceKm;
        this.slots = slots;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getAddress() { return address; }
    public Double getLatitude() { return latitude; }
    public Double getLongitude() { return longitude; }
    public Double getDistanceKm() { return distanceKm; }
    public List<EvChargingSlot> getSlots() { return slots; }
}