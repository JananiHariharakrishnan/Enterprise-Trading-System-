package com.tradingsystem.service;

/**
 * Service to verify user credentials before sensitive operations (e.g., wallet deposit).
 */
public interface PasswordVerifier {

    /**
     * Verify whether the supplied password matches the credentials for the given username.
     *
     * @param username The username of the account holder
     * @param password The plaintext or base64-encoded password supplied by the user
     * @return true if credentials are valid, false otherwise
     */
    boolean verifyPassword(String username, String password);
}
