package com.tradingsystem.controller;

import com.tradingsystem.ai.signal.PortfolioContext;
import com.tradingsystem.ai.signal.SignalEngine;
import com.tradingsystem.ai.signal.TradeSignalStore;
import com.tradingsystem.dto.response.TradeSignalResponse;
import com.tradingsystem.mapper.OrderDataMapper;
import com.tradingsystem.model.Account;
import com.tradingsystem.model.AiSignal;
import com.tradingsystem.model.Position;
import com.tradingsystem.security.CurrentUser;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * REST controller exposing AI-generated trade signals.
 * GET /api/v1/signals                    — only signals for stocks the authenticated user has ordered
 * GET /api/v1/signals/{symbol}           — signal for a specific symbol
 * POST /api/v1/signals/generate/{symbol} — generate on-demand signal for symbol
 * POST /api/v1/signals/refresh           — refresh signals for user's ordered stocks
 */
@RestController
@RequestMapping("/api/v1/signals")
@CrossOrigin(origins = "http://localhost:4200", allowCredentials = "true")
public class SignalController {

    private final TradeSignalStore signalStore;
    private final SignalEngine signalEngine;
    private final CurrentUser currentUser;
    private final OrderDataMapper orderDataMapper;

    public SignalController(
            TradeSignalStore signalStore,
            SignalEngine signalEngine,
            CurrentUser currentUser,
            OrderDataMapper orderDataMapper) {
        this.signalStore = signalStore;
        this.signalEngine = signalEngine;
        this.currentUser = currentUser;
        this.orderDataMapper = orderDataMapper;
    }

    /**
     * Returns the latest AI signal ONLY for stocks the user has actually placed orders for.
     * If the user has not placed any orders, an empty list is returned.
     */
    @GetMapping
    public ResponseEntity<List<TradeSignalResponse>> getAllSignals() {
        Long accountId = getAuthenticatedAccountId();
        if (accountId == null) {
            return ResponseEntity.ok(List.of());
        }

        List<String> orderedSymbols = orderDataMapper.findDistinctOrderedSymbolsByAccountId(accountId);
        if (orderedSymbols == null || orderedSymbols.isEmpty()) {
            return ResponseEntity.ok(List.of());
        }

        List<TradeSignalResponse> signals = new ArrayList<>();
        for (String symbol : orderedSymbols) {
            Optional<AiSignal> stored = signalStore.getBySymbol(symbol);
            AiSignal signal = stored.orElseGet(() -> {
                PortfolioContext pCtx = buildPortfolioContext(accountId, symbol);
                return signalEngine.generateSignalForSymbol(symbol, null, pCtx, true);
            });
            if (signal != null) {
                signals.add(toResponse(signal));
            }
        }

        return ResponseEntity.ok(signals);
    }

    /**
     * Returns the latest AI signal for a single symbol.
     */
    @GetMapping("/{symbol}")
    public ResponseEntity<TradeSignalResponse> getSignal(@PathVariable String symbol) {
        return signalStore.getBySymbol(symbol.toUpperCase())
                .map(this::toResponse)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * On-demand generation of a fresh AI signal for a symbol incorporating user portfolio context.
     */
    @PostMapping("/generate/{symbol}")
    public ResponseEntity<TradeSignalResponse> generateSignal(
            @PathVariable String symbol,
            @RequestParam(required = false) Double price) {
        Long accountId = getAuthenticatedAccountId();
        PortfolioContext pCtx = accountId != null ? buildPortfolioContext(accountId, symbol) : null;
        AiSignal generated = signalEngine.generateSignalForSymbol(symbol, price, pCtx, true);
        if (generated == null) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(toResponse(generated));
    }

    /**
     * Refresh signals ONLY for stocks the user has placed orders for.
     */
    @PostMapping("/refresh")
    public ResponseEntity<List<TradeSignalResponse>> refreshAll() {
        Long accountId = getAuthenticatedAccountId();
        if (accountId == null) {
            return ResponseEntity.ok(List.of());
        }

        List<String> orderedSymbols = orderDataMapper.findDistinctOrderedSymbolsByAccountId(accountId);
        if (orderedSymbols == null || orderedSymbols.isEmpty()) {
            return ResponseEntity.ok(List.of());
        }

        List<TradeSignalResponse> refreshed = new ArrayList<>();
        for (String symbol : orderedSymbols) {
            PortfolioContext pCtx = buildPortfolioContext(accountId, symbol);
            AiSignal generated = signalEngine.generateSignalForSymbol(symbol, null, pCtx, true);
            if (generated != null) {
                refreshed.add(toResponse(generated));
            }
        }
        return ResponseEntity.ok(refreshed);
    }

    private Long getAuthenticatedAccountId() {
        try {
            return currentUser != null ? currentUser.getAccountId() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private PortfolioContext buildPortfolioContext(Long accountId, String symbol) {
        PortfolioContext context = new PortfolioContext();
        context.setAccountId(accountId);
        try {
            Account account = orderDataMapper.findAccountById(accountId);
            if (account != null) {
                context.setCashBalance(account.getCashBalance());
                context.setBuyingPower(account.getBuyingPower());
            }
            Position position = orderDataMapper.findPosition(accountId, symbol);
            if (position != null) {
                context.setCurrentHoldingQuantity(position.getQuantity());
                context.setCurrentAverageCost(position.getAverageCost());
            } else {
                context.setCurrentHoldingQuantity(0);
                context.setCurrentAverageCost(BigDecimal.ZERO);
            }
        } catch (Exception e) {
            // fallback gracefully
        }
        return context;
    }

    private TradeSignalResponse toResponse(AiSignal signal) {
        TradeSignalResponse res = new TradeSignalResponse(
                signal.getSymbol(),
                signal.getAction(),
                signal.getConfidence(),
                signal.getReasoning(),
                signal.getGeneratedAt()
        );
        res.setRsi(signal.getRsi());
        res.setEma9(signal.getEma9());
        res.setEma21(signal.getEma21());
        res.setCurrentPrice(signal.getCurrentPrice());
        res.setMethodology("Wilder's RSI(14) & EMA(9/21) trend crossover on Fauxnance daily candles + live market-data quote");
        res.setDisclaimer("Informational trade advice only — informs a customer who trades; it does not trade or constitute legal financial advice.");
        res.setCandleCacheDuration("24 hours (86,400s) — static EOD candles");
        return res;
    }
}
