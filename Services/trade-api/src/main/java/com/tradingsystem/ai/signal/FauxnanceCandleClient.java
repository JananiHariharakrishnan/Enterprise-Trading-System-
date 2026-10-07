package com.tradingsystem.ai.signal;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Client for fetching daily historical candles from the Fauxnance GET /candles/{symbol} endpoint.
 * Conforms to the stretch extension specification:
 * - Consumes Fauxnance GET /candles/{symbol} for lookback windows.
 * - Extracts closing prices chronologically for technical indicator calculation.
 * - Resilient against network timeouts, non-200 responses, or malformed payloads.
 */
@Component
public class FauxnanceCandleClient {

    private static final Logger log = LoggerFactory.getLogger(FauxnanceCandleClient.class);

    private final String baseUrl;
    private final String apiKey;
    private final String candlesEndpoint;
    private final int timeoutSeconds;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public FauxnanceCandleClient(
            @Value("${fauxnance.api.base-url:https://api.fauxnance.io}") String baseUrl,
            @Value("${fauxnance.api.key:}") String apiKey,
            @Value("${fauxnance.api.candles-endpoint:/candles}") String candlesEndpoint,
            @Value("${fauxnance.api.timeout-seconds:5}") int timeoutSeconds,
            ObjectMapper objectMapper) {
        this.baseUrl = baseUrl != null ? baseUrl.replaceAll("/+$", "") : "https://api.fauxnance.io";
        this.apiKey = apiKey != null ? apiKey.trim() : "";
        this.candlesEndpoint = candlesEndpoint != null ? candlesEndpoint.replaceAll("^/+", "") : "candles";
        this.timeoutSeconds = timeoutSeconds > 0 ? timeoutSeconds : 5;
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(this.timeoutSeconds))
                .build();
    }

    /**
     * Fetch daily historical closing prices for a symbol from Fauxnance GET /candles/{symbol}.
     * Returns a list of valid double closing prices ordered from oldest to newest.
     * Returns an empty list if unavailable or on error.
     */
    public List<Double> fetchDailyCloses(String symbol) {
        if (symbol == null || symbol.isBlank()) {
            return List.of();
        }

        String targetUrl = String.format("%s/%s/%s", baseUrl, candlesEndpoint, symbol.trim().toUpperCase());

        try {
            HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(targetUrl))
                    .timeout(Duration.ofSeconds(timeoutSeconds))
                    .header("Accept", "application/json")
                    .GET();

            if (!apiKey.isEmpty()) {
                reqBuilder.header("X-Api-Key", apiKey);
            }

            HttpResponse<String> response = httpClient.send(reqBuilder.build(), HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                log.warn("FauxnanceCandleClient: GET {} returned HTTP status {}", targetUrl, response.statusCode());
                return List.of();
            }

            return parseCandles(response.body());

        } catch (Exception e) {
            log.warn("FauxnanceCandleClient: failed to fetch candles from {}: {}", targetUrl, e.getMessage());
            return List.of();
        }
    }

    /**
     * Parses the candles JSON response envelope.
     * Handles schema: { "data": { "candles": [ { "date": "...", "close": 123.45 } ] } }
     * and fallback structures: { "candles": [ ... ] } or direct array.
     */
    public List<Double> parseCandles(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            return List.of();
        }

        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode candlesNode = null;

            if (root.has("data") && root.get("data").has("candles")) {
                candlesNode = root.get("data").get("candles");
            } else if (root.has("candles")) {
                candlesNode = root.get("candles");
            } else if (root.isArray()) {
                candlesNode = root;
            }

            if (candlesNode == null || !candlesNode.isArray() || candlesNode.isEmpty()) {
                return List.of();
            }

            List<CandleEntry> entries = new ArrayList<>();
            for (JsonNode c : candlesNode) {
                if (c.has("close") && !c.get("close").isNull() && c.get("close").isNumber()) {
                    double close = c.get("close").asDouble();
                    if (close > 0) {
                        String date = c.has("date") && !c.get("date").isNull() ? c.get("date").asText() : "";
                        entries.add(new CandleEntry(date, close));
                    }
                }
            }

            // Sort chronologically by date if available
            entries.sort(Comparator.comparing(CandleEntry::date));

            List<Double> closes = new ArrayList<>(entries.size());
            for (CandleEntry e : entries) {
                closes.add(e.close());
            }

            log.debug("FauxnanceCandleClient: parsed {} daily candles successfully", closes.size());
            return closes;

        } catch (Exception e) {
            log.warn("FauxnanceCandleClient: JSON parse error: {}", e.getMessage());
            return List.of();
        }
    }

    private record CandleEntry(String date, double close) {}
}
