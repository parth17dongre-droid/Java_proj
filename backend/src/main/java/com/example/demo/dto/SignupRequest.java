package com.example.demo.dto;
import jakarta.validation.constraints.*;

public record SignupRequest(
        @NotBlank String name,
        @NotBlank @Size(max = 100) String username,
        @Email String email,
        @NotBlank @Size(min = 8, max = 72) String password) {
}
