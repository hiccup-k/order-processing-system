package com.example.gateway.controller;

import com.example.gateway.dto.AuthDtos.LoginRequest;
import com.example.gateway.dto.AuthDtos.LoginResponse;
import com.example.gateway.security.InMemoryUserStore;
import com.example.gateway.security.JwtUtil;
import com.example.gateway.security.UserRecord;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final InMemoryUserStore userStore;
    private final JwtUtil jwtUtil;
    private final BCryptPasswordEncoder encoder;
    private final long expirationMinutes;

    public AuthController(InMemoryUserStore userStore, JwtUtil jwtUtil, BCryptPasswordEncoder encoder,
                           @Value("${security.jwt.expiration-minutes:60}") long expirationMinutes) {
        this.userStore = userStore;
        this.jwtUtil = jwtUtil;
        this.encoder = encoder;
        this.expirationMinutes = expirationMinutes;
    }

    @PostMapping("/login")
    public Mono<ResponseEntity<LoginResponse>> login(@Valid @RequestBody LoginRequest request) {
        return Mono.justOrEmpty(userStore.findByUsername(request.getUsername()))
            .filter(user -> encoder.matches(request.getPassword(), user.passwordHash()))
            .map(this::issueToken)
            .defaultIfEmpty(ResponseEntity.status(HttpStatus.UNAUTHORIZED).<LoginResponse>build());
    }

    private ResponseEntity<LoginResponse> issueToken(UserRecord user) {
        String token = jwtUtil.generateToken(user.username(), user.roles());
        return ResponseEntity.ok(new LoginResponse(token, "Bearer", expirationMinutes * 60, user.roles()));
    }
}
