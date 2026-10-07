package com.tradingsystem.ai.signal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SignalCadenceManagerTest {

    @Test
    @DisplayName("SignalCadenceManager suppresses recompute when called within cadence interval")
    void testCadenceThrottling() {
        SignalCadenceManager manager = new SignalCadenceManager();
        try {
            java.lang.reflect.Field field = SignalCadenceManager.class.getDeclaredField("cadenceIntervalSeconds");
            field.setAccessible(true);
            field.set(manager, 60L);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        // Fresh symbol -> should recompute
        assertTrue(manager.shouldRecompute("INFY.NS"));

        // Record recomputation
        manager.recordRecomputation("INFY.NS");

        // Immediately after -> should NOT recompute (respects cadence to suppress noise)
        assertFalse(manager.shouldRecompute("INFY.NS"));

        // Reset cadence -> should recompute immediately
        manager.resetCadence("INFY.NS");
        assertTrue(manager.shouldRecompute("INFY.NS"));
    }
}
