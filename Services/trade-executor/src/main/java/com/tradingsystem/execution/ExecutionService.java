package com.tradingsystem.execution;

import com.tradingsystem.event.payload.OrderPlacedPayload;
import com.tradingsystem.poller.FauxnanceClient;
import com.tradingsystem.poller.Quote;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class ExecutionService {

    private static final Logger log =
            LoggerFactory.getLogger(ExecutionService.class);

    private final ExecutionRule executionRule;
    private final FauxnanceClient fauxnanceClient;
    private final RestTemplate restTemplate;
    private final String tradeApiBaseUrl;

    public ExecutionService(
            ExecutionRule executionRule,
            FauxnanceClient fauxnanceClient,
            RestTemplate restTemplate,
            @Value("${trade.api.base-url:http://localhost:8081}")
            String tradeApiBaseUrl
    ) {
        this.executionRule = executionRule;
        this.fauxnanceClient = fauxnanceClient;
        this.restTemplate = restTemplate;
        this.tradeApiBaseUrl = tradeApiBaseUrl;
    }

    /**
     * Processes an order by obtaining a quote, deciding whether
     * the order should be filled or rejected, and delegating
     * persistence to trade-api.
     */
    public void execute(OrderPlacedPayload order) {

        if (order.getOrderId() == null || order.getOrderId().isBlank()) {
            log.error("Order execution failed: orderId is missing in payload");
            throw new IllegalArgumentException(
                    "Order ID cannot be null or empty"
            );
        }

        UUID orderId = UUID.fromString(order.getOrderId());
        String eventId = order.getEventId();

        /*
         * Obtain quote.
         *
         * FauxnanceWebClient handles transient connection failures
         * by retrying the request.
         *
         * If all attempts fail, the exception is allowed to propagate
         * so Kafka can retry the order.
         */
        List<Quote> quotes =
                fauxnanceClient.getQuotes(List.of(order.getSymbol()));

        /*
         * Fauxnance responded successfully but did not provide
         * a quote for the requested symbol.
         */
        if (quotes == null || quotes.isEmpty()) {

            log.warn(
                    "No quote available for symbol {}. Rejecting order {}",
                    order.getSymbol(),
                    orderId
            );

            callTradeApiApplyDecision(
                    orderId,
                    "REJECTED",
                    null,
                    "Quote unavailable",
                    eventId
            );

            return;
        }

        Quote quote = quotes.get(0);

        ExecutionDecision decision =
                executionRule.decide(
                        order.getSide(),
                        order.getPrice(),
                        quote.price()
                );

        if (decision.getStatus() ==
                ExecutionDecision.Status.FILLED) {

            BigDecimal executedPrice =
                    decision.getExecutedPrice();

            log.info(
                    "Order {} decided FILLED at price {} — "
                            + "delegating DB update to trade-api",
                    orderId,
                    executedPrice
            );

            callTradeApiApplyDecision(
                    orderId,
                    "FILLED",
                    executedPrice,
                    null,
                    eventId
            );

        } else {

            log.info(
                    "Order {} decided REJECTED (reason={}) — "
                            + "delegating DB update to trade-api",
                    orderId,
                    decision.getReason()
            );

            callTradeApiApplyDecision(
                    orderId,
                    "REJECTED",
                    null,
                    decision.getReason(),
                    eventId
            );
        }
    }

    private void callTradeApiApplyDecision(
            UUID orderId,
            String status,
            BigDecimal executedPrice,
            String reason,
            String eventId
    ) {

        String url =
                tradeApiBaseUrl
                        + "/internal/orders/"
                        + orderId
                        + "/execution";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

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

        HttpEntity<Map<String, Object>> entity =
                new HttpEntity<>(payload, headers);

        try {

            ResponseEntity<String> response =
                    restTemplate.postForEntity(
                            URI.create(url),
                            entity,
                            String.class
                    );

            int statusCode =
                    response.getStatusCode().value();

            if (statusCode >= 200 && statusCode < 300) {

                log.info(
                        "trade-api accepted execution for order {} status={}",
                        orderId,
                        status
                );

            } else {

                log.warn(
                        "trade-api returned unexpected status {} "
                                + "for order {}",
                        statusCode,
                        orderId
                );

                throw new IllegalStateException(
                        "Unexpected HTTP status "
                                + statusCode
                                + " from trade-api"
                );
            }

        } catch (HttpClientErrorException hcee) {

            if (hcee.getStatusCode() ==
                    HttpStatus.CONFLICT) {

                log.info(
                        "trade-api indicated order {} already processed "
                                + "(409) — ignoring replay",
                        orderId
                );

            } else {

                log.error(
                        "HTTP error calling trade-api for order {}: {} {}",
                        orderId,
                        hcee.getStatusCode(),
                        hcee.getResponseBodyAsString()
                );

                throw new RuntimeException(
                        "HTTP error calling trade-api",
                        hcee
                );
            }

        } catch (ResourceAccessException rae) {

            log.error(
                    "I/O error calling trade-api for order {}: {}",
                    orderId,
                    rae.getMessage()
            );

            throw new RuntimeException(
                    "I/O error calling trade-api",
                    rae
            );

        } catch (Exception e) {

            log.error(
                    "Failed to call trade-api to apply execution "
                            + "for order {}: {}",
                    orderId,
                    e.getMessage()
            );

            throw new RuntimeException(
                    "Failed to call trade-api",
                    e
            );
        }
    }
}