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
    public ResponseEntity<Map<String, com.tradingsystem.dto.response.LatestPriceResponse>> getAllMarketData() {
        Map<String, LatestMarketPrice> cachedPrices = marketDataService.getAllCachedPrices();
        Map<String, com.tradingsystem.dto.response.LatestPriceResponse> responseMap = new java.util.HashMap<>();

        for (Map.Entry<String, LatestMarketPrice> entry : cachedPrices.entrySet()) {
            responseMap.put(entry.getKey(), toLatestPriceResponse(entry.getValue()));
        }

        return ResponseEntity.ok(responseMap);
    }

    private com.tradingsystem.dto.response.LatestPriceResponse toLatestPriceResponse(LatestMarketPrice latestMarketPrice) {
        if (latestMarketPrice == null) {
            return null;
        }

        com.tradingsystem.dto.response.LatestPriceResponse response = new com.tradingsystem.dto.response.LatestPriceResponse();
        response.setPrice(latestMarketPrice.getPrice());
        response.setCurrency(latestMarketPrice.getCurrency());
        response.setChange(latestMarketPrice.getChange());
        response.setChangePercent(latestMarketPrice.getChangePercent());
        response.setPreviousClose(latestMarketPrice.getPreviousClose());
        response.setMarketState(latestMarketPrice.getMarketState());

        if (latestMarketPrice.getStale() != null) {
            response.setStale(latestMarketPrice.getStale());
        }

        if (latestMarketPrice.getQuoteAsOf() != null) {
            response.setQuoteAsOf(java.time.OffsetDateTime.parse(latestMarketPrice.getQuoteAsOf()));
        }

        return response;
    }
}

