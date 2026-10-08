package com.tradingsystem.security;

import java.util.Optional;

/**
 * Holds request-scoped authentication data for the current thread.
 */
public final class AuthContextHolder {

    private static final ThreadLocal<Long> ACCOUNT_ID = new ThreadLocal<>();
    private static final ThreadLocal<java.util.List<String>> ROLES = new ThreadLocal<>();

    private AuthContextHolder() {
    }

    public static void setAccountId(Long accountId) {
        ACCOUNT_ID.set(accountId);
    }

    public static Optional<Long> getAccountId() {
        return Optional.ofNullable(ACCOUNT_ID.get());
    }

    public static void setRoles(java.util.List<String> roles) {
        if (roles == null || roles.isEmpty()) {
            ROLES.set(java.util.Collections.singletonList("CUSTOMER"));
        } else {
            ROLES.set(java.util.List.copyOf(roles));
        }
    }

    public static Optional<java.util.List<String>> getRoles() {
        return Optional.ofNullable(ROLES.get());
    }

    public static boolean isAdmin() {
        java.util.List<String> roles = ROLES.get();
        return roles != null && roles.contains("ADMIN");
    }

    public static void clear() {
        ACCOUNT_ID.remove();
        ROLES.remove();
    }
}

