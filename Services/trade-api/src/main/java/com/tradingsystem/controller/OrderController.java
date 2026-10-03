package com.tradingsystem.controller;

import com.tradingsystem.dto.request.PlaceOrderRequest;
import com.tradingsystem.dto.response.OrderHistoryEntry;
import com.tradingsystem.dto.response.OrderResponse;
import com.tradingsystem.exception.InvalidOrderException;
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
@CrossOrigin(origins = "http://localhost:4200", allowCredentials = "true")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    public ResponseEntity<OrderResponse> placeOrder(
            @Valid @RequestBody PlaceOrderRequest request) {

        // Require price only for LIMIT orders.
        if (request.getOrderType() == null) {
            throw new InvalidOrderException("Order type is required");
        }

        if (request.getOrderType().name().equalsIgnoreCase("LIMIT") && request.getPrice() == null) {
            throw new InvalidOrderException("Price is required for LIMIT orders");
        }

        PlaceOrderCommand command = new PlaceOrderCommand();
        command.setAccountId(request.getAccountId());
        command.setSymbol(request.getSymbol());
        command.setSide(request.getSide());
        command.setOrderType(request.getOrderType());
        command.setQuantity(request.getQuantity());
        // Only set price when provided (MARKET orders will leave price null)
        if (request.getPrice() != null) {
            command.setPrice(request.getPrice());
        }
        command.setIdempotencyKey(request.getIdempotencyKey());

        OrderResponse response = orderService.placeOrder(command);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<OrderResponse> cancelOrder(
            @PathVariable("id") String id) {

        OrderResponse response = orderService.cancelOrder(id);

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