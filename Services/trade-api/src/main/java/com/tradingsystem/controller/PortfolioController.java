package com.tradingsystem.controller;

import com.tradingsystem.dto.response.MoversResponse;
import com.tradingsystem.dto.response.PnlResponse;
import com.tradingsystem.dto.response.PortfolioSummaryResponse;
import com.tradingsystem.dto.response.PricedPositionResponse;
import com.tradingsystem.service.PortfolioService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/** Contract: portfolio-api.yaml. /movers is an extension beside it. */
@RestController
@RequestMapping("/api/v1/portfolio")
@CrossOrigin(origins = "http://localhost:4200", allowCredentials = "true")
public class PortfolioController {

    private final PortfolioService portfolioService;

    public PortfolioController(PortfolioService portfolioService) {
        this.portfolioService = portfolioService;
    }

    @GetMapping("/{accountId}")
    public ResponseEntity<PortfolioSummaryResponse> getPortfolioSummary(
            @PathVariable("accountId") Long accountId) {

        return ResponseEntity.ok(portfolioService.getSummary(accountId));
    }

    @GetMapping("/{accountId}/positions")
    public ResponseEntity<List<PricedPositionResponse>> getPricedPositions(
            @PathVariable("accountId") Long accountId,
            @RequestParam(value = "symbol", required = false) String symbol) {

        return ResponseEntity.ok(portfolioService.getPricedPositions(accountId, symbol));
    }

    @GetMapping("/{accountId}/pnl")
    public ResponseEntity<PnlResponse> getPnl(
            @PathVariable("accountId") Long accountId,
            @RequestParam(value = "from", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(value = "to", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(value = "bySymbol", defaultValue = "false") boolean bySymbol) {

        return ResponseEntity.ok(portfolioService.getPnl(accountId, from, to, bySymbol));
    }

    @GetMapping("/{accountId}/movers")
    public ResponseEntity<MoversResponse> getMovers(
            @PathVariable("accountId") Long accountId,
            @RequestParam(value = "limit", defaultValue = "5") int limit,
            @RequestParam(value = "basis", defaultValue = "total") String basis) {

        return ResponseEntity.ok(portfolioService.getMovers(accountId, limit, basis));
    }
}
