package com.example.gateway.security;

import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class JwtUtilTest {

    private final JwtUtil jwtUtil = new JwtUtil(
        "test-secret-key-that-is-long-enough-for-hs256-signing", 60);

    @Test
    void generateToken_producesTokenThatParsesBackToSameClaims() {
        String token = jwtUtil.generateToken("alice", List.of("USER"));

        var claims = jwtUtil.tryParse(token);

        assertThat(claims).isPresent();
        Claims c = claims.get();
        assertThat(c.getSubject()).isEqualTo("alice");
        assertThat(c.get("roles", List.class)).containsExactly("USER");
    }

    @Test
    void tryParse_returnsEmptyForGarbageToken() {
        assertThat(jwtUtil.tryParse("not-a-real-jwt")).isEmpty();
    }

    @Test
    void tryParse_returnsEmptyForTokenSignedWithDifferentSecret() {
        JwtUtil otherIssuer = new JwtUtil("a-completely-different-secret-key-value-here", 60);
        String token = otherIssuer.generateToken("mallory", List.of("ADMIN"));

        assertThat(jwtUtil.tryParse(token)).isEmpty();
    }
}
