package com.tradingsystem.security;

import java.util.Collections;
import java.util.List;

/**
 * Verified claims extracted from a valid auth-service JWT token.
 */
public record JwtClaims(
        Long accountId,
        List<String> roles,
        String subject
) {
    public JwtClaims {
        if (roles == null || roles.isEmpty()) {
            roles = Collections.singletonList("CUSTOMER");
        } else {
            roles = List.copyOf(roles);
        }
    }

    public boolean isAdmin() {
        return roles != null && roles.contains("ADMIN");
    }
}
