package com.tradingsystem.controller;

import com.tradingsystem.automation.model.StrategyInstance;
import com.tradingsystem.automation.service.StrategyAutomationService;
import com.tradingsystem.dto.request.CreateStrategyRequest;
import com.tradingsystem.dto.request.SimulateTickRequest;
import com.tradingsystem.dto.response.StrategyResponse;
import com.tradingsystem.messaging.QuotePayload;
import com.tradingsystem.security.AuthContextHolder;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/strategies")
@CrossOrigin(origins = "http://localhost:4200", allowCredentials = "true")
public class StrategyController {

    private final StrategyAutomationService strategyAutomationService;

    public StrategyController(StrategyAutomationService strategyAutomationService) {
        this.strategyAutomationService = strategyAutomationService;
    }

    @PostMapping
    public ResponseEntity<StrategyResponse> createStrategy(@Valid @RequestBody CreateStrategyRequest request) {
        Long accountId = request.getAccountId();
        if (accountId == null) {
            accountId = AuthContextHolder.getAccountId().orElse(1L);
        }

        String strategyId = request.getStrategyId() != null && !request.getStrategyId().isBlank()
                ? request.getStrategyId()
                : "strat-" + UUID.randomUUID().toString().substring(0, 8);

        StrategyInstance instance = new StrategyInstance(
                strategyId,
                accountId,
                request.getSymbol().toUpperCase(),
                request.getType(),
                request.getQuantity(),
                request.getFastPeriod() != null ? request.getFastPeriod() : 5,
                request.getSlowPeriod() != null ? request.getSlowPeriod() : 20,
                request.getBbPeriod() != null ? request.getBbPeriod() : 20,
                request.getBbStdDev() != null ? request.getBbStdDev() : 2.0,
                request.getMaxSpend(),
                request.getMaxPositionSize() != null ? request.getMaxPositionSize() : 500,
                request.getMaxConsecutiveFailures() != null ? request.getMaxConsecutiveFailures() : 3
        );

        strategyAutomationService.registerStrategy(instance);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(StrategyResponse.fromInstance(instance));
    }

    @GetMapping
    public ResponseEntity<List<StrategyResponse>> getAllStrategies() {
        List<StrategyResponse> responses = strategyAutomationService.getAllStrategies().stream()
                .map(StrategyResponse::fromInstance)
                .collect(Collectors.toList());

        return ResponseEntity.ok(responses);
    }

    @GetMapping("/{id}")
    public ResponseEntity<StrategyResponse> getStrategy(@PathVariable("id") String id) {
        StrategyInstance instance = strategyAutomationService.getStrategy(id);
        if (instance == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(StrategyResponse.fromInstance(instance));
    }

    @PostMapping("/{id}/disable")
    public ResponseEntity<StrategyResponse> disableStrategy(@PathVariable("id") String id) {
        strategyAutomationService.disableStrategy(id);
        StrategyInstance instance = strategyAutomationService.getStrategy(id);
        if (instance == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(StrategyResponse.fromInstance(instance));
    }

    @PostMapping("/{id}/enable")
    public ResponseEntity<StrategyResponse> enableStrategy(@PathVariable("id") String id) {
        strategyAutomationService.enableStrategy(id);
        StrategyInstance instance = strategyAutomationService.getStrategy(id);
        if (instance == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(StrategyResponse.fromInstance(instance));
    }

    @PostMapping("/simulate-tick")
    public ResponseEntity<Map<String, Object>> simulateTick(@Valid @RequestBody SimulateTickRequest request) {
        QuotePayload quote = new QuotePayload();
        quote.setSymbol(request.getSymbol().toUpperCase());
        quote.setPrice(request.getPrice());
        quote.setCurrency("USD");
        quote.setMarketState("open");
        quote.setStale(false);
        quote.setQuoteAsOf(java.time.OffsetDateTime.now(java.time.ZoneOffset.UTC).toString());

        List<String> results = strategyAutomationService.onQuote(quote);

        return ResponseEntity.ok(Map.of(
                "status", "TICK_PROCESSED",
                "symbol", request.getSymbol().toUpperCase(),
                "price", request.getPrice(),
                "results", results
        ));
    }
}

