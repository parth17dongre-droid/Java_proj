package com.example.demo.dto;

public record AuthResponse(String message) {
    public static AuthResponse ok() {
        return new AuthResponse("success");
    }
}