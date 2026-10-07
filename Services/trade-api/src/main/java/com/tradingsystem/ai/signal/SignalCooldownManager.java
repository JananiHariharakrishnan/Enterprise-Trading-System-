package com.tradingsystem.ai.signal;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks per-symbol signal cooldown periods to prevent the LLM from being
 * called too frequently for the same symbol.
 * A scheduled task sweeps expired entries every 30 seconds.
 */
@Component
public class SignalCooldownManager {

    /** symbol → cooldown expiry timestamp */
    private final ConcurrentHashMap<String, Instant> cooldowns = new ConcurrentHashMap<>();

    @Value("${signal.cooldown-seconds:60}")
    private long cooldownSeconds;

    /**
     * Returns true if the symbol is currently in its cooldown window.
     */
    public boolean isOnCooldown(String symbol) {
        Instant expiry = cooldowns.get(symbol);
        return expiry != null && Instant.now().isBefore(expiry);
    }

    /**
     * Marks the symbol as being in cooldown for the configured number of seconds.
     */
    public void markCooldown(String symbol) {
        cooldowns.put(symbol, Instant.now().plusSeconds(cooldownSeconds));
    }

    /**
     * Resets the cooldown for the symbol so a fresh signal can be generated immediately.
     */
    public void resetCooldown(String symbol) {
        if (symbol != null) {
            cooldowns.remove(symbol);
            cooldowns.remove(symbol.toUpperCase());
        }
    }

    /**
     * Scheduled sweep that removes expired cooldown entries to prevent unbounded growth.
     * Runs every 30 seconds.
     */
    @Scheduled(fixedDelay = 30_000)
    public void sweepExpired() {
        Instant now = Instant.now();
        cooldowns.entrySet().removeIf(entry -> now.isAfter(entry.getValue()));
    }
}
