package com.tradingsystem.controller;

import com.tradingsystem.dto.request.WalletDepositRequest;
import com.tradingsystem.dto.response.WalletDepositResponse;
import com.tradingsystem.exception.TradingAuthenticationException;
import com.tradingsystem.model.Account;
import com.tradingsystem.security.AuthContextHolder;
import com.tradingsystem.service.WalletService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/wallet")
public class WalletController {

    private final WalletService walletService;

    public WalletController(WalletService walletService) {
        this.walletService = walletService;
    }

    private Long resolveAccountId() {
        return AuthContextHolder.getAccountId()
                .orElseThrow(() -> new TradingAuthenticationException("Unauthorised: Missing account authentication"));
    }

    @GetMapping
    public ResponseEntity<Account> getWalletDetails() {
        Long accountId = resolveAccountId();
        Account account = walletService.getWalletDetails(accountId);
        return ResponseEntity.ok(account);
    }

    @PostMapping("/deposit")
    public ResponseEntity<WalletDepositResponse> depositFunds(@Valid @RequestBody WalletDepositRequest request) {
        Long accountId = resolveAccountId();
        WalletDepositResponse response = walletService.depositFunds(accountId, request);
        return ResponseEntity.ok(response);
    }
}
