package com.example.gateway.security;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Deliberately in-memory: this project scopes itself to 4 services (gateway, order,
 * inventory, notification) rather than adding a full user-service + user DB. In a real
 * system this would be replaced by a call to a user-service or an identity provider
 * (Cognito/Auth0/Keycloak) — the JWT issuance contract (JwtUtil) wouldn't need to change.
 */
@Component
public class InMemoryUserStore {

    private final Map<String, UserRecord> users;

    public InMemoryUserStore(BCryptPasswordEncoder encoder) {
        this.users = Map.of(
            "alice", new UserRecord("alice", encoder.encode("password123"), List.of("USER")),
            "admin", new UserRecord("admin", encoder.encode("adminpass123"), List.of("USER", "ADMIN"))
        );
    }

    public Optional<UserRecord> findByUsername(String username) {
        return Optional.ofNullable(users.get(username));
    }
}
