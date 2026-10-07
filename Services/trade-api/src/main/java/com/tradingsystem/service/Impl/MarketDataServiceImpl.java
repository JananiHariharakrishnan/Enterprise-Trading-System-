package com.tradingsystem.service.Impl;

import com.tradingsystem.dto.response.LatestMarketPrice;
import com.tradingsystem.messaging.QuotePayload;
import com.tradingsystem.service.MarketDataService;
import com.tradingsystem.service.PriceAlertEvaluationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Processes market-data quotes and maintains an in-memory cache
 * of the latest quote keyed by symbol.
 *
 * The cache contains only the latest quote for each symbol.
 * Historical market-data is not stored here.
 * 
 * Delegates price alert evaluation to PriceAlertEvaluationService
 * for triggering alerts based on price crossings.
 */
@Service
public class MarketDataServiceImpl implements MarketDataService {

    private static final Logger logger =
            LoggerFactory.getLogger(MarketDataServiceImpl.class);

    private static final String EXPECTED_EVENT_TYPE = "QUOTE";

    /**
     * Latest quote for each symbol.
     */
    private final ConcurrentHashMap<String, LatestMarketPrice> priceCache =
            new ConcurrentHashMap<>();

    /**
     * Previous price for each symbol.
     * Used to detect price crossings for alert evaluation.
     */
    private final ConcurrentHashMap<String, BigDecimal> priceHistory =
            new ConcurrentHashMap<>();

    /**
     * Processed event IDs used for idempotency.
     *
     * Kafka provides at-least-once delivery, so the same event can
     * potentially be delivered more than once.
     */
    private final ConcurrentHashMap<String, String> processedEvents =
            new ConcurrentHashMap<>();

    private final PriceAlertEvaluationService priceAlertEvaluationService;

    public MarketDataServiceImpl(
            PriceAlertEvaluationService priceAlertEvaluationService
    ) {
        this.priceAlertEvaluationService = priceAlertEvaluationService;
    }

    @Override
    public void processQuote(
            String eventId,
            String eventType,
            QuotePayload quote
    ) {

        // Validate event type
        if (!EXPECTED_EVENT_TYPE.equals(eventType)) {
            throw new IllegalArgumentException(
                    "Invalid eventType for market-data: expected 'QUOTE', got '"
                            + eventType + "'"
            );
        }

        // Validate payload
        if (quote == null) {
            throw new IllegalArgumentException(
                    "Quote payload cannot be null"
            );
        }

        if (quote.getSymbol() == null || quote.getSymbol().isBlank()) {
            throw new IllegalArgumentException(
                    "Quote symbol cannot be null or empty"
            );
        }

        if (quote.getPrice() == null) {
            throw new IllegalArgumentException(
                    "Quote price cannot be null for symbol: "
                            + quote.getSymbol()
            );
        }

        // Idempotency check
        String previousSymbol =
                processedEvents.putIfAbsent(
                        eventId,
                        quote.getSymbol()
                );

        if (previousSymbol != null) {

            if (!previousSymbol.equals(quote.getSymbol())) {
                logger.warn(
                        "Duplicate eventId {} with different symbol. Previous: {}, Current: {}",
                        eventId,
                        previousSymbol,
                        quote.getSymbol()
                );
            } else {
                logger.debug(
                        "Duplicate event {} for symbol {} already processed",
                        eventId,
                        quote.getSymbol()
                );
            }

            return;
        }

        // Convert Kafka payload into the cached representation
        LatestMarketPrice latestPrice = new LatestMarketPrice(
                quote.getSymbol(),
                quote.getPrice(),
                quote.getCurrency(),
                quote.getChange(),
                quote.getChangePercent(),
                quote.getPreviousClose(),
                quote.getMarketState(),
                quote.getStale(),
                quote.getQuoteAsOf()
        );

        // Get previous price for this symbol (if any)
        BigDecimal previousPrice = priceHistory.get(quote.getSymbol());

        // Replace the previous quote for this symbol
        priceCache.put(
                quote.getSymbol(),
                latestPrice
        );

        // Evaluate ACTIVE price alerts for this symbol
        // Only evaluate if we have a previous price (can't detect crossing on first quote)
        if (previousPrice != null) {
            try {
                priceAlertEvaluationService.evaluateAlertsForSymbol(
                        quote.getSymbol(),
                        previousPrice,
                        quote.getPrice()
                );
            } catch (Exception e) {
                logger.error(
                        "Error evaluating alerts for symbol {}",
                        quote.getSymbol(),
                        e
                );
                // Don't fail the quote processing if alert evaluation fails
            }
        }

        // Update price history for next quote
        priceHistory.put(quote.getSymbol(), quote.getPrice());

        logger.info(
                "Updated price cache for {}: {} {} ({})",
                quote.getSymbol(),
                quote.getPrice(),
                quote.getCurrency(),
                quote.getMarketState()
        );
    }

    /**
     * Returns the latest quote for a symbol.
     */
    @Override
    public LatestMarketPrice  getLatestPrice(String symbol) {
        return priceCache.get(symbol);
    }

    /**
     * Returns all currently cached quotes.
     */
    public Map<String, LatestMarketPrice> getAllCachedPrices() {
        return Collections.unmodifiableMap(
                new HashMap<>(priceCache)
        );
    }

    /**
     * Clears the cache.
     *
     * Intended primarily for testing.
     */
    public void clearCache() {
        priceCache.clear();
        priceHistory.clear();
        processedEvents.clear();
    }

    /**
     * Checks whether an event has already been processed.
     */
    public boolean isEventProcessed(String eventId) {
        return processedEvents.containsKey(eventId);
    }
}