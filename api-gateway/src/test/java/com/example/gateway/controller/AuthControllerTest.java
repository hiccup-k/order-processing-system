package com.example.gateway.controller;

import com.example.gateway.security.InMemoryUserStore;
import com.example.gateway.security.JwtUtil;
import com.example.gateway.security.UserRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock
    private InMemoryUserStore userStore;

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
    private final JwtUtil jwtUtil = new JwtUtil("test-secret-key-that-is-long-enough-for-hs256", 60);

    private WebTestClient client() {
        AuthController controller = new AuthController(userStore, jwtUtil, encoder, 60);
        return WebTestClient.bindToController(controller).build();
    }

    @Test
    void login_returnsTokenForValidCredentials() {
        UserRecord alice = new UserRecord("alice", encoder.encode("password123"), List.of("USER"));
        when(userStore.findByUsername("alice")).thenReturn(Optional.of(alice));

        client().post().uri("/auth/login")
            .bodyValue(Map("alice", "password123"))
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.accessToken").isNotEmpty()
            .jsonPath("$.tokenType").isEqualTo("Bearer")
            .jsonPath("$.roles[0]").isEqualTo("USER");
    }

    @Test
    void login_returns401ForWrongPassword() {
        UserRecord alice = new UserRecord("alice", encoder.encode("password123"), List.of("USER"));
        when(userStore.findByUsername("alice")).thenReturn(Optional.of(alice));

        client().post().uri("/auth/login")
            .bodyValue(Map("alice", "wrong-password"))
            .exchange()
            .expectStatus().isUnauthorized();
    }

    @Test
    void login_returns401ForUnknownUser() {
        when(userStore.findByUsername("ghost")).thenReturn(Optional.empty());

        client().post().uri("/auth/login")
            .bodyValue(Map("ghost", "whatever"))
            .exchange()
            .expectStatus().isUnauthorized();
    }

    private java.util.Map<String, String> Map(String username, String password) {
        return java.util.Map.of("username", username, "password", password);
    }
}
