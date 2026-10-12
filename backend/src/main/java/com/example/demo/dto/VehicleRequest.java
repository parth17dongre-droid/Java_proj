package com.example.demo.dto;

import com.example.demo.model.Vehicle;
import jakarta.validation.constraints.*;

import java.util.Set;

public class VehicleRequest {

    @NotBlank
    private String make;

    @NotBlank
    private String model;

    @NotNull
    @Min(2010)
    @Max(2030)
    private Integer year;

    @NotNull
    @Positive
    private Double batteryCapacityKwh;

    @Positive
    private Double maxChargingPowerKw;

    @NotEmpty
    private Set<Vehicle.ConnectorType> connectorTypes;

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

    public Set<Vehicle.ConnectorType> getConnectorTypes() { return connectorTypes; }
    public void setConnectorTypes(Set<Vehicle.ConnectorType> connectorTypes) { this.connectorTypes = connectorTypes; }

    // Response DTO nested here to avoid an extra file.
    // Keeps the password-bearing Users entity out of the JSON.
    public record VehicleResponse(
            Long id,
            String make,
            String model,
            Integer year,
            Double batteryCapacityKwh,
            Double maxChargingPowerKw,
            Set<Vehicle.ConnectorType> connectorTypes
    ) {
        public static VehicleResponse from(Vehicle v) {
            return new VehicleResponse(
                    v.getId(), v.getMake(), v.getModel(), v.getYear(),
                    v.getBatteryCapacityKwh(), v.getMaxChargingPowerKw(),
                    v.getConnectorTypes()
            );
        }
    }
}