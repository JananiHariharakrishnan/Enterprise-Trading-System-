package com.tradingsystem.security;

/**
 * Provides the identity of the currently authenticated user/account.
 *
 * The service layer depends on this abstraction rather than parsing
 * Authorization headers or JWTs directly.
 */
public interface CurrentUser {

    Long getAccountId();
} 
