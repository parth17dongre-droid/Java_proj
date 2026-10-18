package com.example.demo.config;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.evcharge.wallet.service.WalletService;
import com.example.demo.model.EvChargingSlot;
import com.example.demo.model.EvChargingStation;
import com.example.demo.model.Users;
import com.example.demo.repository.EvChargingStationRepository;
import com.example.demo.repository.UserRepository;

/** Fills an empty database with Pune stations and (optionally) an admin account, so the demo works out of the box. */
@Component
@Order(2)
public class DataSeeder implements ApplicationRunner {

    private final EvChargingStationRepository stations;
    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final WalletService wallets;
    private final String adminPassword;

    public DataSeeder(EvChargingStationRepository stations, UserRepository users, PasswordEncoder encoder,
                      WalletService wallets, @Value("${seed.admin-password:}") String adminPassword) {
        this.stations = stations;
        this.users = users;
        this.encoder = encoder;
        this.wallets = wallets;
        this.adminPassword = adminPassword;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (stations.count() == 0) {
            stations.save(station("Koregaon Park Hub", "Lane 5, Koregaon Park, Pune", 18.5362, 73.8940));
            stations.save(station("Shivajinagar Charge Point", "FC Road, Shivajinagar, Pune", 18.5314, 73.8446));
            stations.save(station("Hinjewadi Tech Park", "Phase 1, Hinjewadi, Pune", 18.5912, 73.7389));
        }
        if (!adminPassword.isBlank() && !users.existsByUsername("admin")) {
            Users admin = new Users("Administrator", "admin", "admin@evbolts.local", encoder.encode(adminPassword));
            admin.setRole("ADMIN");
            wallets.createWallet(users.save(admin).getId());
        }
    }

    private static EvChargingStation station(String name, String address, double lat, double lng) {
        EvChargingStation st = new EvChargingStation();
        st.setName(name);
        st.setAddress(address);
        st.setLatitude(lat);
        st.setLongitude(lng);
        List<EvChargingSlot> slots = new ArrayList<>();
        slots.add(slot(st, "F1", "CCS2", "FAST", "30.00", "18.00"));
        slots.add(slot(st, "F2", "CCS2", "FAST", "30.00", "18.00"));
        slots.add(slot(st, "F3", "CHADEMO", "FAST", "25.00", "18.00"));
        slots.add(slot(st, "S1", "TYPE_2", "SLOW", "7.40", "10.00"));
        slots.add(slot(st, "S2", "TYPE_2", "SLOW", "7.40", "10.00"));
        st.setSlots(slots);
        return st;
    }

    private static EvChargingSlot slot(EvChargingStation st, String number, String connector, String type,
                                       String kw, String rate) {
        EvChargingSlot s = new EvChargingSlot();
        s.setStation(st);
        s.setSlotNumber(number);
        s.setConnectorType(connector);
        s.setChargerType(type);
        s.setPowerKw(new BigDecimal(kw));
        s.setRatePerKwh(new BigDecimal(rate));
        return s;
    }
}
