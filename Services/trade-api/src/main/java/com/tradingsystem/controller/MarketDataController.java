package com.tradingsystem.controller;

import com.tradingsystem.dto.response.LatestMarketPrice;
import com.tradingsystem.service.MarketDataService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/market-data")
@CrossOrigin(origins = "http://localhost:4200", allowCredentials = "true")
public class MarketDataController {

    private final MarketDataService marketDataService;

    public MarketDataController(MarketDataService marketDataService) {
        this.marketDataService = marketDataService;
    }

    @GetMapping
    public ResponseEntity<Map<String, LatestMarketPrice>> getAllMarketData() {
        return ResponseEntity.ok(marketDataService.getAllCachedPrices());
    }
}

