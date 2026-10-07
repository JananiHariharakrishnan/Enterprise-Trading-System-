package com.tradingsystem.controller;

import com.tradingsystem.dto.request.CreateAccountRequest;
import com.tradingsystem.dto.request.UpdateStatusRequest;
import com.tradingsystem.dto.response.AdminDashboardStats;
import com.tradingsystem.dto.response.AdminUserSummary;
import com.tradingsystem.dto.response.UnassignedAccountSummary;
import com.tradingsystem.exception.AdminAccessDeniedException;
import com.tradingsystem.model.Account;
import com.tradingsystem.security.AuthContextHolder;
import com.tradingsystem.service.AdminService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminController {

    private final AdminService adminService;

    public AdminController(AdminService adminService) {
        this.adminService = adminService;
    }

    private void enforceAdminAccess() {
        if (!AuthContextHolder.isAdmin()) {
            throw new AdminAccessDeniedException();
        }
    }

    @GetMapping("/stats")
    public ResponseEntity<AdminDashboardStats> getDashboardStats() {
        enforceAdminAccess();
        return ResponseEntity.ok(adminService.getDashboardStats());
    }

    @GetMapping("/users")
    public ResponseEntity<List<AdminUserSummary>> getAllUsers() {
        enforceAdminAccess();
        return ResponseEntity.ok(adminService.getAllUsers());
    }

    @GetMapping("/accounts/unassigned")
    public ResponseEntity<List<UnassignedAccountSummary>> getUnassignedAndDormantAccounts() {
        enforceAdminAccess();
        return ResponseEntity.ok(adminService.getUnassignedAndDormantAccounts());
    }

    @PutMapping("/users/{userId}/status")
    public ResponseEntity<Void> updateUserStatus(
            @PathVariable("userId") UUID userId,
            @Valid @RequestBody UpdateStatusRequest request
    ) {
        enforceAdminAccess();
        adminService.updateUserAccountStatus(userId, request.getStatus());
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/accounts/{accountId}/status")
    public ResponseEntity<Void> updateAccountStatus(
            @PathVariable("accountId") Long accountId,
            @Valid @RequestBody UpdateStatusRequest request
    ) {
        enforceAdminAccess();
        adminService.updateAccountStatus(accountId, request.getStatus());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/accounts")
    public ResponseEntity<Account> createUnmappedAccount(
            @RequestBody(required = false) CreateAccountRequest request
    ) {
        enforceAdminAccess();
        Account created = adminService.createUnmappedAccount(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PostMapping("/accounts/batch")
    public ResponseEntity<List<Account>> createBatchAccounts(
            @RequestBody(required = false) CreateAccountRequest request
    ) {
        enforceAdminAccess();
        int count = (request != null && request.getCount() != null && request.getCount() > 0)
                ? request.getCount()
                : 1;
        BigDecimal balance = (request != null && request.getInitialBalance() != null)
                ? request.getInitialBalance()
                : new BigDecimal("100000.00");
        List<Account> created = adminService.createBatchAccounts(count, balance);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }
}
