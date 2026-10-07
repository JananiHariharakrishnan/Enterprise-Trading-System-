package com.tradingsystem.controller;

import com.tradingsystem.dto.response.HealthResponse;
import com.tradingsystem.service.PortfolioService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * GET /health. Outside /api/v1, so the JWT filter does not apply (the contract marks it
 * unauthenticated). Always 200 while the service runs; a failing dependency degrades status.
 */
@RestController
public class PortfolioHealthController {

    private final PortfolioService portfolioService;

    public PortfolioHealthController(PortfolioService portfolioService) {
        this.portfolioService = portfolioService;
    }

    @GetMapping("/health")
    public ResponseEntity<HealthResponse> getHealth() {
        return ResponseEntity.ok(portfolioService.getHealth());
    }
}
