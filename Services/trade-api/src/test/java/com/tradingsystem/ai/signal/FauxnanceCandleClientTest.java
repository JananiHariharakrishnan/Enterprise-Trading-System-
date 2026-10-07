package com.tradingsystem.ai.signal;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FauxnanceCandleClientTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("parseCandles correctly extracts closes from standard Fauxnance envelope")
    void testParseCandlesStandardSchema() {
        FauxnanceCandleClient client = new FauxnanceCandleClient("https://api.fauxnance.io", "test-key", "/candles", 5, objectMapper);

        String json = """
                {
                  "data": {
                    "symbol": "INFY.NS",
                    "interval": "1d",
                    "candles": [
                      { "date": "2026-07-01", "close": 1584.5 },
                      { "date": "2026-07-02", "close": 1599.0 },
                      { "date": "2026-07-03", "close": 1591.6 }
                    ]
                  },
                  "meta": { "asOf": "2026-07-03T16:00:00Z" }
                }
                """;

        List<Double> closes = client.parseCandles(json);
        assertEquals(3, closes.size());
        assertEquals(1584.5, closes.get(0));
        assertEquals(1599.0, closes.get(1));
        assertEquals(1591.6, closes.get(2));
    }

    @Test
    @DisplayName("parseCandles handles empty or malformed payload gracefully")
    void testParseCandlesEmpty() {
        FauxnanceCandleClient client = new FauxnanceCandleClient("https://api.fauxnance.io", "test-key", "/candles", 5, objectMapper);

        assertTrue(client.parseCandles("").isEmpty());
        assertTrue(client.parseCandles(null).isEmpty());
        assertTrue(client.parseCandles("{}").isEmpty());
        assertTrue(client.parseCandles("{\"data\": {\"candles\": []}}").isEmpty());
        assertTrue(client.parseCandles("not valid json").isEmpty());
    }
}
