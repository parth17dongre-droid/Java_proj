package com.example.demo.service;

import com.example.demo.dto.VehicleRequest;
import com.example.demo.dto.VehicleRequest.VehicleResponse;
import com.example.demo.model.Users;
import com.example.demo.model.Vehicle;
import com.example.demo.repository.UserRepository;
import com.example.demo.repository.VehicleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class VehicleService {

    private final VehicleRepository vehicleRepository;
    private final UserRepository userRepository;

    public VehicleService(VehicleRepository vehicleRepository, UserRepository userRepository) {
        this.vehicleRepository = vehicleRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public VehicleResponse addVehicle(String username, VehicleRequest request) {
        Users user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        Vehicle vehicle = new Vehicle();
        vehicle.setOwner(user);
        vehicle.setMake(request.getMake().trim());
        vehicle.setModel(request.getModel().trim());
        vehicle.setYear(request.getYear());
        vehicle.setBatteryCapacityKwh(request.getBatteryCapacityKwh());
        vehicle.setMaxChargingPowerKw(request.getMaxChargingPowerKw());
        vehicle.setConnectorTypes(request.getConnectorTypes());

        return VehicleResponse.from(vehicleRepository.save(vehicle));
    }

    @Transactional(readOnly = true)
    public List<VehicleResponse> getMyVehicles(String username) {
        Users user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        return vehicleRepository.findByOwnerId(user.getId()).stream()
                .map(VehicleResponse::from)
                .toList();
    }
}