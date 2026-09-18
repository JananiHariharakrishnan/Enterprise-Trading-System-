package com.tradingsystem.execution;

import com.tradingsystem.event.payload.OrderPlacedPayload;
import com.tradingsystem.poller.FauxnanceClient;
import com.tradingsystem.poller.Quote;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;
import org.springframework.beans.factory.annotation.Value;

import java.math.BigDecimal;
import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * ExecutionService: decides on fills/rejects and delegates database updates to the trade-api.
 *
 * Responsibilities:
 *  - Obtain quotes, decide filled/rejected.
 *  - Call trade-api internal endpoint to apply the decision and let trade-api persist orders/executions/balances.
 *  - Handle responses from trade-api (409 = already handled, 200 = success).
 *
 * Notes:
 *  - This service no longer writes to the database. The trade-api now performs the guarded status update,
 *    inserts into executions, applies account/position updates and publishes the trade-event after commit.
 */
@Service
public class ExecutionService {

    private static final Logger log = LoggerFactory.getLogger(ExecutionService.class);

    private final ExecutionRule executionRule;
    private final FauxnanceClient fauxnanceClient;
    private final RestTemplate restTemplate;

    // Trade API URL base - configure via env or property in production. Keep simple here.
    private final String tradeApiBaseUrl;

    public ExecutionService(
            ExecutionRule executionRule,
            FauxnanceClient fauxnanceClient,
            RestTemplate restTemplate,
            @Value("${trade.api.base-url:http://localhost:8888}") String tradeApiBaseUrl
    ) {
        this.executionRule = executionRule;
        this.fauxnanceClient = fauxnanceClient;
        this.restTemplate = restTemplate;
        this.tradeApiBaseUrl = tradeApiBaseUrl;
    }

    /**
     * Process an order: decide and call trade-api to persist the decision.
     *
     * @param order incoming order event payload
     */
    public void execute(OrderPlacedPayload order) {

        if (order.getOrderId() == null || order.getOrderId().isBlank()) {
            log.error("Order execution failed: orderId is missing in payload");
            throw new IllegalArgumentException("Order ID cannot be null or empty");
        }

        UUID orderId = UUID.fromString(order.getOrderId());
        String eventId = order.getEventId(); // Extract event ID for idempotency

        // 1) Obtain quotes and decide
        List<Quote> quotes = fauxnanceClient.getQuotes(List.of(order.getSymbol()));

        if (quotes == null || quotes.isEmpty()) {
            // Call trade-api to mark REJECTED due to quote unavailable
            callTradeApiApplyDecision(orderId, "REJECTED", null, "Quote unavailable", eventId);
            return;
        }

        Quote quote = quotes.get(0);

        ExecutionDecision decision =
                executionRule.decide(
                        order.getSide(),
                        order.getPrice(),
                        quote.price()
                );

        if (decision.getStatus() == ExecutionDecision.Status.FILLED) {

            BigDecimal executedPrice = decision.getExecutedPrice();

            log.info("Order {} decided FILLED at price {} — delegating DB update to trade-api", orderId, executedPrice);

            callTradeApiApplyDecision(orderId, "FILLED", executedPrice, null, eventId);

        } else {
            log.info("Order {} decided REJECTED (reason={}) — delegating DB update to trade-api", orderId, decision.getReason());
            callTradeApiApplyDecision(orderId, "REJECTED", null, decision.getReason(), eventId);
        }
    }
    
    private void callTradeApiApplyDecision(UUID orderId,
                                           String status,
                                           BigDecimal executedPrice,
                                           String reason,
                                           String eventId) {

        String url = tradeApiBaseUrl + "/internal/orders/" + orderId.toString() + "/execution";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        // Build a payload map, but only include non-null values to avoid Map.of(null) NPEs
        Map<String, Object> payload = new HashMap<>();
        payload.put("status", status);
        if (executedPrice != null) {
            payload.put("executedPrice", executedPrice);
        }
        if (reason != null) {
            payload.put("reason", reason);
        }
        if (eventId != null) {
            payload.put("eventId", eventId);
        }

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(payload, headers);

        try {
            ResponseEntity<String> response = restTemplate.postForEntity(URI.create(url), entity, String.class);

            int statusCode = response.getStatusCode().value();
            if (statusCode >= 200 && statusCode < 300) {
                log.info("trade-api accepted execution for order {} status={}", orderId, status);
            } else {
                log.warn("trade-api returned unexpected status {} for order {}", statusCode, orderId);
                // For unexpected 4xx/5xx we want to surface an error so the consumer can retry.
                throw new RuntimeException("Unexpected HTTP status " + statusCode + " from trade-api");
            }

        } catch (HttpClientErrorException hcee) {
            if (hcee.getStatusCode() == HttpStatus.CONFLICT) {
                // 409 Conflict - treat as duplicate/already processed
                log.info("trade-api indicated order {} already processed (409) — ignoring replay", orderId);
                return;
            } else {
                log.error("HTTP error calling trade-api for order {}: {} {}", orderId, hcee.getStatusCode(), hcee.getResponseBodyAsString());
                throw new RuntimeException("HTTP error calling trade-api", hcee);
            }
        } catch (ResourceAccessException rae) {
            // I/O level error (e.g. connection refused / timeout)
            log.error("I/O error calling trade-api for order {}: {}", orderId, rae.getMessage());
            throw new RuntimeException("I/O error calling trade-api", rae);
        } catch (Exception e) {
            log.error("Failed to call trade-api to apply execution for order {}: {}", orderId, e.getMessage());
            throw new RuntimeException("Failed to call trade-api", e);
        }
    }
}