package com.tradingsystem.service;

import com.tradingsystem.model.User;

import java.util.UUID;

public interface UserService {

    /**
     * Find an available account ID for user registration
     */
    Long getAvailableAccountId();

    /**
     * Find a user by username
     */
    User getUserByUsername(String username);

    /**
     * Find a user by ID
     */
    User getUserById(UUID id);

    /**
     * Find a user by account ID
     */
    User getUserByAccountId(Long accountId);

    /**
     * Create a new user with the given username and account ID
     */
    void createUser(String username, Long accountId, String[] roles);

    /**
     * Update user profile information
     */
    User updateUserProfile(UUID id, String firstName, String lastName, String email, String phoneNo);

    /**
     * Check if an account exists
     */
    boolean accountExists(Long accountId);

    /**
     * Delete orphan credentials (credentials without associated users)
     */
    int deleteOrphanCredentials();
}
