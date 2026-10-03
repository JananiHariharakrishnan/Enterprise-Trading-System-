package com.tradingsystem.controller;

import com.tradingsystem.dto.response.AccountResponse;
import com.tradingsystem.dto.response.BalanceResponse;
import com.tradingsystem.dto.response.OrderHistoryEntry;
import com.tradingsystem.dto.response.PositionResponse;
import com.tradingsystem.service.AccountService;
import com.tradingsystem.service.OrderService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/v1/accounts")
@CrossOrigin(origins = "http://localhost:4200", allowCredentials = "true")
public class AccountController {

    private final AccountService accountService;
    private final OrderService orderService;

    public AccountController(
            AccountService accountService,
            OrderService orderService) {

        this.accountService = accountService;
        this.orderService = orderService;
    }

    @GetMapping("/{id}")
    public ResponseEntity<AccountResponse> getAccount(
            @PathVariable("id") Long id) {

        return ResponseEntity.ok(
                accountService.getAccount(id)
        );
    }

    @GetMapping("/{id}/balance")
    public ResponseEntity<BalanceResponse> getBalance(
            @PathVariable("id") Long id) {

        return ResponseEntity.ok(
                accountService.getBalance(id)
        );
    }

    @GetMapping("/{id}/positions")
    public ResponseEntity<List<PositionResponse>> getPositions(
            @PathVariable("id") Long id) {

        return ResponseEntity.ok(
                accountService.getPositions(id)
        );
    }

    @GetMapping("/{id}/orders")
    public ResponseEntity<List<OrderHistoryEntry>> getOrders(
            @PathVariable("id") Long id,
            @RequestParam(required = false)
            String status,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            OffsetDateTime from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            OffsetDateTime to) {

        return ResponseEntity.ok(
                orderService.getOrderHistory(
                        id,
                        status,
                        from,
                        to
                )
        );
    }

}