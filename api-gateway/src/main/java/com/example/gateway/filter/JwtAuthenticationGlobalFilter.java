package com.example.gateway.filter;

import com.example.gateway.security.JwtUtil;
import io.jsonwebtoken.Claims;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Optional;

/**
 * Rejects unauthenticated/invalid-token requests to protected routes before they leave
 * the gateway, and forwards the verified identity downstream as X-User-Id / X-User-Roles
 * headers. Order-service and inventory-service still re-validate the JWT themselves
 * (defense in depth: a service should never blindly trust a header that could be spoofed
 * if it were ever reachable directly, e.g. inside the cluster network).
 */
@Component
public class JwtAuthenticationGlobalFilter implements GlobalFilter, Ordered {

    private static final List<String> PUBLIC_PATHS = List.of("/auth/login", "/actuator/health");

    private final JwtUtil jwtUtil;

    public JwtAuthenticationGlobalFilter(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();
        if (PUBLIC_PATHS.stream().anyMatch(path::startsWith)) {
            return chain.filter(exchange);
        }

        ServerHttpRequest request = exchange.getRequest();
        String header = request.getHeaders().getFirst("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            return unauthorized(exchange);
        }

        Optional<Claims> claims = jwtUtil.tryParse(header.substring(7));
        if (claims.isEmpty()) {
            return unauthorized(exchange);
        }

        Claims c = claims.get();
        String roles = String.join(",", c.get("roles", List.class));
        ServerHttpRequest mutated = request.mutate()
            .header("X-User-Id", c.getSubject())
            .header("X-User-Roles", roles)
            .build();

        return chain.filter(exchange.mutate().request(mutated).build());
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        return response.setComplete();
    }

    @Override
    public int getOrder() {
        return -1;
    }
}
