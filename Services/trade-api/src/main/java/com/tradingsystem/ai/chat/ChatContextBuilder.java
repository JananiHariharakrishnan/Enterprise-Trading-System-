package com.tradingsystem.ai.chat;

import com.tradingsystem.ai.signal.TradeSignalStore;
import com.tradingsystem.dto.response.OrderHistoryEntry;
import com.tradingsystem.mapper.AccountDataMapper;
import com.tradingsystem.mapper.OrderDataMapper;
import com.tradingsystem.model.Account;
import com.tradingsystem.model.Position;
import com.tradingsystem.service.MarketDataService;
import com.tradingsystem.service.WatchlistService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Assembles the full system prompt sent to the LLM for the chatbot,
 * injecting live portfolio, watchlist, signal, and market price context.
 */
@Component
public class ChatContextBuilder {

    private static final Logger log = LoggerFactory.getLogger(ChatContextBuilder.class);

    private final TradeSignalStore signalStore;
    private final MarketDataService marketDataService;
    private final WatchlistService watchlistService;
    private final AccountDataMapper accountDataMapper;
    private final OrderDataMapper orderDataMapper;

    public ChatContextBuilder(
            TradeSignalStore signalStore,
            MarketDataService marketDataService,
            WatchlistService watchlistService,
            AccountDataMapper accountDataMapper,
            OrderDataMapper orderDataMapper) {
        this.signalStore = signalStore;
        this.marketDataService = marketDataService;
        this.watchlistService = watchlistService;
        this.accountDataMapper = accountDataMapper;
        this.orderDataMapper = orderDataMapper;
    }

    /**
     * Build the full prompt string to send to the LLM.
     *
     * @param accountId   the authenticated user's account ID
     * @param userMessage the user's question
     * @return full prompt including system context + user message
     */
    public String build(Long accountId, String userMessage) {
        String portfolioSummary = buildPortfolioSummary(accountId);
        String signalsSummary = buildSignalsSummary();
        String marketPricesSummary = buildMarketPricesSummary();
        String watchlistSummary = buildWatchlistSummary(accountId);

        return String.format("""
                You are an intelligent quantitative trading assistant embedded in the Firodha trading platform.
                You provide institutional trade advice and technical analysis to assist self-directed traders.

                === CORE PLATFORM PHILOSOPHY & LEGAL CONSTRAINTS ===
                - "It informs a customer who trades; it does not trade."
                - Your advice is informational decision support to empower the customer; you never execute trades autonomously or provide legal financial guarantees.
                - We employ ONE single defensible quantitative methodology computed from real daily candles:
                  * 14-period Wilder's Relative Strength Index (RSI) for momentum.
                  * 9-period EMA (fast) and 21-period EMA (slow) crossover for trend direction.
                - Architecture & Data Sources:
                  * Streaming market-data Kafka topic reacts to "now" (live real-time quotes).
                  * Fauxnance GET /candles/{symbol} provides lookback windows of real daily historical candles.
                - Cost & Cadence Decisions:
                  * End-of-day candles do not change during the trading day: they are cached for 24 hours (86,400s) to protect API quota.
                  * Streaming quotes arrive continuously, but signals recompute on a fixed 60-second cadence to filter noise and conserve computation.

                === PLATFORM SIGNAL ENGINE & TECHNICAL INDICATORS ===
                - RSI (Relative Strength Index, 14-period Wilder smoothing):
                  * < 30: oversold / bullish accumulation setup.
                  * > 70: overbought / distribution warning.
                  * 30-70: neutral momentum.
                - EMA Crossovers (9-period fast EMA vs 21-period slow EMA):
                  * Bullish alignment: EMA(9) >= EMA(21) confirms upward trend momentum.
                  * Bearish crossover: EMA(9) < EMA(21) signals downward distribution pressure.
                - Signals render:
                  1. Direction: BUY, SELL, or HOLD.
                  2. Strength: Confidence / conviction (0.72 – 0.94).
                  3. Sentence: Exactly 1 crisp sentence explaining what produced it and citing the numbers behind it (RSI, EMA 9, EMA 21, price).

                === LIVE USER PORTFOLIO & RECENT ORDERS ===
                %s

                === LIVE PLATFORM CONTEXT ===
                AI SIGNALS (latest):
                %s

                MARKET PRICES (latest cached):
                %s

                USER WATCHLISTS:
                %s

                === REGULATORY & BEHAVIORAL GUARDRAILS ===
                - You are an expert quantitative trading assistant providing articulate, high-conviction, professional insights.
                - Uphold the legal boundary: "It informs a customer who trades; it does not trade." You NEVER execute orders, access trading keys, or claim to have placed orders. Always direct the user to click the Trade button or use the Order Ticket.
                - NEVER promise or imply guaranteed returns or risk-free trades. All signals are probabilistic momentum/trend indicators.
                - Defend our single robust methodology (Wilder RSI 14 + EMA 9/21 on 24h-cached daily candles + live quotes).
                - When the user asks about ANY stock or their portfolio, provide a clear quantitative breakdown referencing their portfolio scenario (holdings/cash/orders) alongside active signal, RSI momentum, and EMA trend numbers.
                - When the user asks how RSI or EMA work or why signals are structured this way, explain our methodology and the cost/cadence design (24h EOD candle caching and 60s cadence).
                - NEVER mention or imply "low data available", "insufficient history", "lack of data", or "flat price movement".
                - Refuse to discuss non-financial or off-topic subjects outside trading, finance, technical analysis, and portfolio tracking.
                - Only reference valid stocks present in the platform context or database.

                User question: %s
                """,
                portfolioSummary,
                signalsSummary,
                marketPricesSummary,
                watchlistSummary,
                userMessage);
    }

    private String buildPortfolioSummary(Long accountId) {
        if (accountId == null) return "No user account linked.";
        try {
            StringBuilder sb = new StringBuilder();
            Account account = accountDataMapper.findAccountById(accountId);
            if (account != null) {
                sb.append(String.format("Account ID: %d | Cash Balance: $%.2f | Buying Power: $%.2f%n",
                        account.getAccountId(), account.getCashBalance(), account.getBuyingPower()));
            }

            List<Position> positions = accountDataMapper.findPositionsByAccountId(accountId);
            if (positions != null && !positions.isEmpty()) {
                sb.append("Current Holdings:\n");
                for (Position p : positions) {
                    if (p.getQuantity() > 0) {
                        sb.append(String.format("  - %s: %d shares @ avg cost $%.2f%n",
                                p.getSymbol(), p.getQuantity(), p.getAverageCost()));
                    }
                }
            } else {
                sb.append("Current Holdings: No open positions.\n");
            }

            List<OrderHistoryEntry> orders = orderDataMapper.findOrderHistory(accountId, null, null, null);
            if (orders != null && !orders.isEmpty()) {
                sb.append("Recent Orders / Transactions:\n");
                int limit = Math.min(orders.size(), 5);
                for (int i = 0; i < limit; i++) {
                    OrderHistoryEntry o = orders.get(i);
                    sb.append(String.format("  - %s %s %d shares @ %s (status: %s)%n",
                            o.getSide(), o.getSymbol(), o.getQuantity(),
                            o.getPrice() != null ? "$" + o.getPrice() : "MARKET",
                            o.getStatus()));
                }
            } else {
                sb.append("Recent Orders: No order history yet.\n");
            }

            return sb.toString();
        } catch (Exception e) {
            log.warn("ChatContextBuilder: could not fetch portfolio summary for account {}: {}", accountId, e.getMessage());
            return "Portfolio data unavailable.";
        }
    }

    private String buildSignalsSummary() {
        try {
            return signalStore.buildSummary();
        } catch (Exception e) {
            log.warn("ChatContextBuilder: could not fetch signals: {}", e.getMessage());
            return "Signal data unavailable.";
        }
    }

    private String buildMarketPricesSummary() {
        try {
            var prices = marketDataService.getAllCachedPrices();
            if (prices.isEmpty()) return "No market prices cached yet.";
            StringBuilder sb = new StringBuilder();
            prices.forEach((symbol, price) -> {
                if (price != null && price.getPrice() != null) {
                    sb.append(String.format("  %s: %.4f %s%n",
                            symbol,
                            price.getPrice().doubleValue(),
                            price.getCurrency() != null ? price.getCurrency() : ""));
                }
            });
            return sb.toString().isEmpty() ? "No market prices cached yet." : sb.toString();
        } catch (Exception e) {
            log.warn("ChatContextBuilder: could not fetch market prices: {}", e.getMessage());
            return "Market price data unavailable.";
        }
    }

    private String buildWatchlistSummary(Long accountId) {
        try {
            var watchlists = watchlistService.listWatchlists(accountId);
            if (watchlists.isEmpty()) return "No watchlists found.";
            StringBuilder sb = new StringBuilder();
            watchlists.forEach(w -> sb.append(String.format("  Watchlist: %s (id=%d)%n", w.getName(), w.getId())));
            return sb.toString();
        } catch (Exception e) {
            log.warn("ChatContextBuilder: could not fetch watchlists for account {}: {}", accountId, e.getMessage());
            return "Watchlist data unavailable.";
        }
    }
}
