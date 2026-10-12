package com.example.demo.controller;

import com.example.demo.dto.VehicleRequest;
import com.example.demo.dto.VehicleRequest.VehicleResponse;
import com.example.demo.service.VehicleService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/vehicles")
public class VehicleController {

    private final VehicleService vehicleService;

    public VehicleController(VehicleService vehicleService) {
        this.vehicleService = vehicleService;
    }

    @PostMapping
    public ResponseEntity<VehicleResponse> addVehicle(
            @Valid @RequestBody VehicleRequest request,
            Authentication authentication) {

        VehicleResponse saved = vehicleService.addVehicle(authentication.getName(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @GetMapping
    public ResponseEntity<List<VehicleResponse>> getMyVehicles(Authentication authentication) {
        return ResponseEntity.ok(vehicleService.getMyVehicles(authentication.getName()));
    }
}