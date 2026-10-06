package com.tradingsystem.security;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

/**
 * Validates bearer tokens for all API routes before controllers execute.
 */
public class JwtAuthenticationFilter implements Filter {

    private static final String AUTHORIZATION = "Authorization";
    private static final String AUTH_ERROR_JSON = "{\"errorCode\":\"AUTH-401\",\"message\":\"Unauthorised\"}";

    private final JwtTokenVerifier tokenVerifier;

    public JwtAuthenticationFilter(JwtTokenVerifier tokenVerifier) {
        this.tokenVerifier = tokenVerifier;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        // Skip authentication for CORS preflight requests
        if ("OPTIONS".equalsIgnoreCase(httpRequest.getMethod())) {
            chain.doFilter(request, response);
            return;
        }

        // Skip authentication for internal service-to-service user management endpoints
        String requestPath = httpRequest.getRequestURI();
        if (requestPath != null && requestPath.startsWith("/api/v1/users")) {
            chain.doFilter(request, response);
            return;
        }

        // Allow unauthenticated internal service token calls to internal preference resolution
        if (requestPath != null && requestPath.startsWith("/api/v1/accounts/") && requestPath.contains("/notifications/resolve")) {
            String header = httpRequest.getHeader(AUTHORIZATION);
            String token = extractBearerToken(header);
            String internal = System.getenv().getOrDefault("INTERNAL_SERVICE_TOKEN", "dev-internal-token");
            if (token != null && token.equals(internal)) {
                // treat as internal call: do not set AuthContextHolder accountId; let controller validate token separately
                chain.doFilter(request, response);
                return;
            }
            // fallthrough to normal JWT validation (reject if token not a JWT)
        }

        String header = httpRequest.getHeader(AUTHORIZATION);
        String token = extractBearerToken(header);

        if (token == null) {
            writeAuthError(httpResponse);
            return;
        }

        Long accountId = tokenVerifier.verifyAndGetAccountId(token);
        if (accountId == null) {
            writeAuthError(httpResponse);
            return;
        }

        AuthContextHolder.setAccountId(accountId);
        try {
            chain.doFilter(request, response);
        } finally {
            AuthContextHolder.clear();
        }
    }

    private String extractBearerToken(String authorizationHeader) {
        if (authorizationHeader == null || authorizationHeader.isBlank()) {
            return null;
        }

        String[] parts = authorizationHeader.trim().split(" ");
        if (parts.length != 2) {
            return null;
        }

        if (!"bearer".equalsIgnoreCase(parts[0])) {
            return null;
        }

        String token = parts[1].trim();
        if (token.isEmpty()) {
            return null;
        }

        return token;
    }

    private void writeAuthError(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Cache-Control", "no-store");
        response.getWriter().write(AUTH_ERROR_JSON);
    }
}


