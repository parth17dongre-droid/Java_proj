package com.example.demo.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.demo.repository.UserRepository;

@RestController
@RequestMapping("/api/users")
public class UserController {

    public record MeResponse(Long id, String name, String username, String email, String role) {}

    private final UserRepository userRepository;

    public UserController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @GetMapping("/me")
    public ResponseEntity<MeResponse> me(Authentication authentication) {
        return userRepository.findByUsername(authentication.getName())
                .map(u -> ResponseEntity.ok(new MeResponse(u.getId(), u.getName(), u.getUsername(), u.getEmail(), u.getRole())))
                .orElse(ResponseEntity.notFound().build());
    }
}
