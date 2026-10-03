package com.tradingsystem.service;

import com.tradingsystem.dto.response.LatestMarketPrice;
import com.tradingsystem.messaging.QuotePayload;

/**
 * Service to handle market-data events and maintain latest price cache.
 * Responsible for all business logic related to price updates.
 * The Kafka listener delegates all processing to this service.
 */
public interface MarketDataService {

    /**
     * Process a market-data quote event and update the latest price cache.
     * Implementation must handle idempotency using eventId.
     *
     * @param eventId unique event identifier for idempotency
     * @param eventType type of event (should be "QUOTE")
     * @param quote the quote payload to process
     * @throws IllegalArgumentException if eventType is not "QUOTE" or quote is invalid
     */
    void processQuote(String eventId, String eventType, QuotePayload quote);

    /**
     * Get the latest cached price for a symbol.
     * Returns null if symbol has no cached price yet.
     *
     * @param symbol the instrument symbol
     * @return the latest price or null if not cached
     */
    LatestMarketPrice getLatestPrice(String symbol);
}

