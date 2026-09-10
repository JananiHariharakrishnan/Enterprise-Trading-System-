package com.tradingsystem.controller;

import com.tradingsystem.dto.request.PlaceOrderRequest;
import com.tradingsystem.dto.response.OrderHistoryEntry;
import com.tradingsystem.dto.response.OrderResponse;
import com.tradingsystem.service.OrderService;
import com.tradingsystem.service.command.PlaceOrderCommand;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    public ResponseEntity<OrderResponse> placeOrder(
            @Valid @RequestBody PlaceOrderRequest request) {

        PlaceOrderCommand command = new PlaceOrderCommand();
        command.setAccountId(request.getAccountId());
        command.setSymbol(request.getSymbol());
        command.setSide(request.getSide());
        command.setOrderType(request.getOrderType());
        command.setQuantity(request.getQuantity());
        command.setPrice(request.getPrice());
        command.setIdempotencyKey(request.getIdempotencyKey());

        OrderResponse response = orderService.placeOrder(command);

        // EDGE CASE FIX: Return 201 CREATED instead of 200 OK for new resources
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<OrderResponse> cancelOrder(
            @PathVariable("id") String id) {

        OrderResponse response = orderService.cancelOrder(id);

        // EDGE CASE FIX: Return 200 OK with proper status
        return ResponseEntity.ok(response);
    }

    @GetMapping("/../accounts/{id}/orders")
    public ResponseEntity<List<OrderHistoryEntry>> getOrderHistory(
            @PathVariable("id") Long accountId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            OffsetDateTime from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            OffsetDateTime to) {

        List<OrderHistoryEntry> response =
                orderService.getOrderHistory(accountId, status, from, to);

        return ResponseEntity.ok(response);
    }

}

