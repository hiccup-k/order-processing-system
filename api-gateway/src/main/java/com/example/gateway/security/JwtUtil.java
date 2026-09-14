package com.example.gateway.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Optional;

@Component
public class JwtUtil {

    private final SecretKey key;
    private final long expirationMillis;

    public JwtUtil(@Value("${security.jwt.secret}") String secret,
                    @Value("${security.jwt.expiration-minutes:60}") long expirationMinutes) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes());
        this.expirationMillis = expirationMinutes * 60_000;
    }

    public String generateToken(String username, List<String> roles) {
        Instant now = Instant.now();
        return Jwts.builder()
            .subject(username)
            .claim("roles", roles)
            .issuedAt(Date.from(now))
            .expiration(Date.from(now.plusMillis(expirationMillis)))
            .signWith(key)
            .compact();
    }

    public Optional<Claims> tryParse(String token) {
        try {
            return Optional.of(Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload());
        } catch (JwtException | IllegalArgumentException ex) {
            return Optional.empty();
        }
    }
}
