package com.tradingsystem.exception;

/**
 * Thrown when a non-admin user attempts to access an admin-only endpoint.
 * Mapped to HTTP 403 Forbidden.
 */
public class AdminAccessDeniedException extends TradingDomainException {
    public AdminAccessDeniedException() {
        super("AUTH-403", "Access denied: Administrator privileges required");
    }
}
