package com.tradingsystem.ai.signal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Enforces the signal recomputation cadence.
 *
 * Addresses the stretch extension requirement on Cadence:
 * "Recomputing a signal on every quote is expensive and mostly noise,
 * while recomputing on a fixed interval is usually enough."
 *
 * Quotes continuously update live prices, but full AI/LLM signal generation
 * is throttled to a fixed interval (default 60 seconds).
 */
@Component
public class SignalCadenceManager {

    private static final Logger log = LoggerFactory.getLogger(SignalCadenceManager.class);

    @Value("${signal.cadence-interval-seconds:60}")
    private long cadenceIntervalSeconds;

    /** symbol -> timestamp of last full signal calculation */
    private final ConcurrentHashMap<String, Instant> lastRecomputed = new ConcurrentHashMap<>();

    /**
     * Determines whether enough time has elapsed since the last signal computation
     * to warrant recomputing a new signal.
     */
    public boolean shouldRecompute(String symbol) {
        if (symbol == null || symbol.isBlank()) return false;
        String normalized = symbol.trim().toUpperCase();

        Instant lastTime = lastRecomputed.get(normalized);
        if (lastTime == null) {
            return true;
        }

        Instant now = Instant.now();
        Duration elapsed = Duration.between(lastTime, now);
        return elapsed.getSeconds() >= cadenceIntervalSeconds;
    }

    /**
     * Record that a signal was just calculated at this moment.
     */
    public void recordRecomputation(String symbol) {
        if (symbol == null || symbol.isBlank()) return;
        lastRecomputed.put(symbol.trim().toUpperCase(), Instant.now());
    }

    /**
     * Force the next evaluation to recompute immediately (e.g. for on-demand user refresh).
     */
    public void resetCadence(String symbol) {
        if (symbol != null) {
            lastRecomputed.remove(symbol.trim().toUpperCase());
        }
    }

    public long getCadenceIntervalSeconds() {
        return cadenceIntervalSeconds;
    }

    public String getCadenceDescription() {
        return String.format("%d seconds — Market-data quotes update streaming prices immediately, while signal generation runs on a fixed %ds cadence to suppress noise and control cost.",
                cadenceIntervalSeconds, cadenceIntervalSeconds);
    }
}
