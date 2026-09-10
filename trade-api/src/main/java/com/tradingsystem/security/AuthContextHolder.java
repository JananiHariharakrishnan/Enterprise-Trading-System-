package com.tradingsystem.security;

import java.util.Optional;

/**
 * Holds request-scoped authentication data for the current thread.
 */
public final class AuthContextHolder {

    private static final ThreadLocal<Long> ACCOUNT_ID = new ThreadLocal<>();

    private AuthContextHolder() {
    }

    public static void setAccountId(Long accountId) {
        ACCOUNT_ID.set(accountId);
    }

    public static Optional<Long> getAccountId() {
        return Optional.ofNullable(ACCOUNT_ID.get());
    }

    public static void clear() {
        ACCOUNT_ID.remove();
    }
}

