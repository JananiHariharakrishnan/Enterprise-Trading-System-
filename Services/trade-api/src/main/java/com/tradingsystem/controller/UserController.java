package com.tradingsystem.controller;

import com.tradingsystem.dto.request.CreateUserRequest;
import com.tradingsystem.dto.request.UpdateUserProfileRequest;
import com.tradingsystem.dto.response.AvailableAccountResponse;
import com.tradingsystem.dto.response.UserResponse;
import com.tradingsystem.model.User;
import com.tradingsystem.service.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * UserController provides internal API endpoints for auth-service to manage user-related operations.
 * These endpoints are NOT protected by JWT authentication as they are designed for
 * internal service-to-service communication only. External clients should NOT have access
 * to these endpoints in production - network isolation or API gateway rules should
 * restrict access to internal services only.
 * 
 * These endpoints bypass JWT authentication intentionally:
 * - /api/v1/users/* - Internal user management for auth-service
 * 
 * See JwtAuthenticationFilter for the exclusion logic.
 */
@RestController
@RequestMapping("/api/v1/users")
@CrossOrigin(originPatterns = "*", allowCredentials = "true")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    /**
     * Get an available account ID for new user registration
     */
    @GetMapping("/available-account")
    public ResponseEntity<AvailableAccountResponse> getAvailableAccount() {
        Long accountId = userService.getAvailableAccountId();
        if (accountId == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        return ResponseEntity.ok(new AvailableAccountResponse(accountId));
    }

    /**
     * Create a new user
     */
    @PostMapping
    public ResponseEntity<UserResponse> createUser(@RequestBody CreateUserRequest request) {
        try {
            userService.createUser(request.getUsername(), request.getAccountId(), request.getRoles());
            User user = userService.getUserByUsername(request.getUsername());
            return ResponseEntity.status(HttpStatus.CREATED).body(mapToResponse(user));
        } catch (Exception e) {
            // Return 409 Conflict if account is already taken or other constraint violation
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }
    }

    /**
     * Get user by ID
     */
    @GetMapping("/{id}")
    public ResponseEntity<UserResponse> getUserById(@PathVariable UUID id) {
        User user = userService.getUserById(id);
        if (user == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(mapToResponse(user));
    }

    /**
     * Get user by username
     */
    @GetMapping("/by-username/{username}")
    public ResponseEntity<UserResponse> getUserByUsername(@PathVariable String username) {
        User user = userService.getUserByUsername(username);
        if (user == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(mapToResponse(user));
    }

    /**
     * Get user by account ID
     */
    @GetMapping("/by-account/{accountId}")
    public ResponseEntity<UserResponse> getUserByAccountId(@PathVariable Long accountId) {
        User user = userService.getUserByAccountId(accountId);
        if (user == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(mapToResponse(user));
    }

    /**
     * Update user profile
     */
    @PutMapping("/{id}/profile")
    public ResponseEntity<UserResponse> updateUserProfile(
            @PathVariable UUID id,
            @RequestBody UpdateUserProfileRequest request) {
        try {
            User updated = userService.updateUserProfile(
                    id,
                    request.getFirstName(),
                    request.getLastName(),
                    request.getEmail(),
                    request.getPhoneNo()
            );
            return ResponseEntity.ok(mapToResponse(updated));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Delete orphan credentials (credentials without associated users)
     */
    @DeleteMapping("/orphan-credentials")
    public ResponseEntity<Void> deleteOrphanCredentials() {
        userService.deleteOrphanCredentials();
        return ResponseEntity.ok().build();
    }

    private UserResponse mapToResponse(User user) {
        UserResponse response = new UserResponse();
        response.setId(user.getId());
        response.setAccountId(user.getAccountId());
        response.setUsername(user.getUsername());
        response.setFirstName(user.getFirstName());
        response.setLastName(user.getLastName());
        response.setEmail(user.getEmail());
        response.setPhoneNo(user.getPhoneNo());
        response.setRoles(user.getRoles());
        response.setCreatedOn(user.getCreatedOn());
        return response;
    }
}
