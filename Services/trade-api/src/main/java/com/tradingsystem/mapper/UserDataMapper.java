package com.tradingsystem.mapper;

import com.tradingsystem.model.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.UUID;

@Mapper
public interface UserDataMapper {

    /**
     * Find an available account ID that is not yet associated with a user.
     * Returns NULL if no available account exists.
     */
    Long findAvailableAccountId();

    /**
     * Find a user by username
     */
    User findUserByUsername(@Param("username") String username);

    /**
     * Find a user by ID
     */
    User findUserById(@Param("id") UUID id);

    /**
     * Find a user by account ID
     */
    User findUserByAccountId(@Param("accountId") Long accountId);

    /**
     * Create a new user
     */
    int createUser(
            @Param("username") String username,
            @Param("accountId") Long accountId,
            @Param("roles") String roles
    );

    /**
     * Update user profile information
     */
    int updateUserProfile(
            @Param("id") UUID id,
            @Param("firstName") String firstName,
            @Param("lastName") String lastName,
            @Param("email") String email,
            @Param("phoneNo") String phoneNo
    );

    /**
     * Check if an account exists
     */
    boolean accountExists(@Param("accountId") Long accountId);

    /**
     * Count orphan credentials (credentials without associated users)
     */
    Long countOrphanCredentials();

    /**
     * Delete orphan credentials
     */
    int deleteOrphanCredentials();

    /**
     * Admin query: find all registered users along with their account details
     */
    java.util.List<com.tradingsystem.model.AdminUserRecord> findAllUsersWithAccountDetails();
}
