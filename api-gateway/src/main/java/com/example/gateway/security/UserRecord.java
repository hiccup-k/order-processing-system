package com.example.gateway.security;

import java.util.List;

public record UserRecord(String username, String passwordHash, List<String> roles) {
}
