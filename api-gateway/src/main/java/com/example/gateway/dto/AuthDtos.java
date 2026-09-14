package com.example.gateway.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.List;

public class AuthDtos {

    public static class LoginRequest {
        @NotBlank
        private String username;
        @NotBlank
        private String password;

        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
    }

    public record LoginResponse(String accessToken, String tokenType, long expiresInSeconds, List<String> roles) {
    }
}
