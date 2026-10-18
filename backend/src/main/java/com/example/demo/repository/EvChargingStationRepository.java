package com.example.demo.repository;

import com.example.demo.model.EvChargingStation;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EvChargingStationRepository extends JpaRepository<EvChargingStation, Long> {
}