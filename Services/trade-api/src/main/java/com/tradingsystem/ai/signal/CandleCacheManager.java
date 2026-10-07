package com.tradingsystem.ai.signal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages an in-memory TTL cache of historical daily candles.
 * 
 * Addresses the stretch extension requirement on Cost and Cadence:
 * "End-of-day candles do not change during the day, so refetching them per request
 * burns quota for an answer that cannot have moved: cache them, and be able to say for how long."
 *
 * Defaults to 24-hour TTL (86,400 seconds) since EOD candles do not change during market hours.
 */
@Component
public class CandleCacheManager {

    private static final Logger log = LoggerFactory.getLogger(CandleCacheManager.class);

    @Value("${signal.candle-cache-ttl-hours:24}")
    private long cacheTtlHours;

    private final FauxnanceCandleClient candleClient;
    private final ConcurrentHashMap<String, CachedCandleSeries> cache = new ConcurrentHashMap<>();

    public CandleCacheManager(FauxnanceCandleClient candleClient) {
        this.candleClient = candleClient;
    }

    /**
     * Retrieve closing prices for symbol from cache or fetch from Fauxnance.
     * If cached and valid within TTL, returns cached data immediately without burning API quota.
     */
    public List<Double> getClosingPrices(String symbol) {
        if (symbol == null || symbol.isBlank()) {
            return List.of();
        }
        String normalizedSymbol = symbol.trim().toUpperCase();

        CachedCandleSeries cached = cache.get(normalizedSymbol);
        Instant now = Instant.now();

        if (cached != null && now.isBefore(cached.expiresAt()) && !cached.closes().isEmpty()) {
            log.debug("CandleCacheManager: Cache HIT for {} (expires in {}m)",
                    normalizedSymbol, Duration.between(now, cached.expiresAt()).toMinutes());
            return cached.closes();
        }

        // Cache MISS or expired: fetch from Fauxnance
        log.info("CandleCacheManager: Cache MISS for {} — fetching from Fauxnance GET /candles/{}",
                normalizedSymbol, normalizedSymbol);
        List<Double> fetched = candleClient.fetchDailyCloses(normalizedSymbol);

        if (!fetched.isEmpty()) {
            Duration ttl = Duration.ofHours(cacheTtlHours > 0 ? cacheTtlHours : 24);
            Instant expiresAt = now.plus(ttl);
            CachedCandleSeries newEntry = new CachedCandleSeries(
                    normalizedSymbol,
                    List.copyOf(fetched),
                    now,
                    expiresAt,
                    ttl.toSeconds()
            );
            cache.put(normalizedSymbol, newEntry);
            log.info("CandleCacheManager: Cached {} candles for {} with TTL of {}h ({}s)",
                    fetched.size(), normalizedSymbol, ttl.toHours(), ttl.toSeconds());
            return newEntry.closes();
        }

        // Fallback: if previous expired cache exists, return it rather than failing
        if (cached != null && !cached.closes().isEmpty()) {
            log.warn("CandleCacheManager: Fauxnance fetch failed for {}, serving stale cached candles", normalizedSymbol);
            return cached.closes();
        }

        return List.of();
    }

    /**
     * Store explicitly seeded or pre-fetched candles (e.g., in testing or initialization).
     */
    public void put(String symbol, List<Double> closes) {
        if (symbol == null || closes == null || closes.isEmpty()) return;
        String normalized = symbol.trim().toUpperCase();
        Instant now = Instant.now();
        Duration ttl = Duration.ofHours(cacheTtlHours > 0 ? cacheTtlHours : 24);
        cache.put(normalized, new CachedCandleSeries(normalized, List.copyOf(closes), now, now.plus(ttl), ttl.toSeconds()));
    }

    /**
     * Clear cache for a specific symbol or all symbols.
     */
    public void invalidate(String symbol) {
        if (symbol != null) {
            cache.remove(symbol.trim().toUpperCase());
        }
    }

    public void clear() {
        cache.clear();
    }

    /**
     * Returns the human-readable explanation of how long candles are cached.
     * Fulfills: "cache them, and be able to say for how long."
     */
    public String getCacheDurationDescription() {
        long hours = cacheTtlHours > 0 ? cacheTtlHours : 24;
        return String.format("%d hours (%d seconds) — End-of-day candles remain static during trading hours until daily settlement.",
                hours, hours * 3600);
    }

    public Optional<CachedCandleSeries> getCacheMetadata(String symbol) {
        if (symbol == null) return Optional.empty();
        return Optional.ofNullable(cache.get(symbol.trim().toUpperCase()));
    }

    public record CachedCandleSeries(
            String symbol,
            List<Double> closes,
            Instant fetchedAt,
            Instant expiresAt,
            long ttlSeconds
    ) {}
}
