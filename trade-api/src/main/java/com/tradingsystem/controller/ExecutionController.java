package com.tradingsystem.controller;

import com.tradingsystem.dto.request.ExecutionRequest;
import com.tradingsystem.exception.DuplicateOrderException;
import com.tradingsystem.service.Impl.ExecutionProcessingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Internal controller for receiving execution decisions from the trade-executor.
 */
@RestController
@RequestMapping("/internal/orders")
public class ExecutionController {

    private static final Logger log = LoggerFactory.getLogger(ExecutionController.class);

    private final ExecutionProcessingService executionProcessingService;

    public ExecutionController(ExecutionProcessingService executionProcessingService) {
        this.executionProcessingService = executionProcessingService;
    }

    @PostMapping("/{orderId}/execution")
    public ResponseEntity<?> applyExecution(@PathVariable("orderId") String orderId, @RequestBody ExecutionRequest request) {
        try {
            executionProcessingService.applyExecution(UUID.fromString(orderId), request);
            return ResponseEntity.ok().build();
        } catch (DuplicateOrderException de) {
            // Already processed
            return ResponseEntity.status(409).body("Order already processed");
        } catch (IllegalArgumentException ie) {
            return ResponseEntity.badRequest().body(ie.getMessage());
        } catch (Exception e) {
            log.error("Failed to apply execution for order {}: {}", orderId, e.getMessage());
            return ResponseEntity.status(500).body("Internal error");
        }
    }
}