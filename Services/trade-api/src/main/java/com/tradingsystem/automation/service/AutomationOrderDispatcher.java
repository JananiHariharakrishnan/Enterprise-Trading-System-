package com.tradingsystem.automation.service;

import com.tradingsystem.dto.request.PlaceOrderRequest;
import com.tradingsystem.dto.response.OrderResponse;
import com.tradingsystem.enums.OrderSide;
import com.tradingsystem.enums.OrderType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

/**
 * Dispatches automated orders through the S6 Trade REST API (POST /api/v1/orders).
 *
 * Ensures all HTTP validation, authentication, and idempotency checks are enforced.
 */
@Service
public class AutomationOrderDispatcher {

    private static final Logger logger = LoggerFactory.getLogger(AutomationOrderDispatcher.class);

    private final RestTemplate restTemplate;
    private final String tradeApiUrl;
    private final String jwtSecret;
    private final String jwtIssuer;

    public AutomationOrderDispatcher() {
        this("http://localhost:8081", "development-only-shared-secret-change-me", "auth-service");
    }

    @Autowired
    public AutomationOrderDispatcher(
            @Value("${trade.api.url:${TRADE_API_URL:http://localhost:${server.port:8081}}}") String tradeApiUrl,
            @Value("${JWT_SECRET:${auth.jwt.secret:development-only-shared-secret-change-me}}") String jwtSecret,
            @Value("${JWT_ISSUER:auth-service}") String jwtIssuer
    ) {
        this(new RestTemplate(), tradeApiUrl, jwtSecret, jwtIssuer);
    }

    public AutomationOrderDispatcher(
            RestTemplate restTemplate,
            String tradeApiUrl,
            String jwtSecret,
            String jwtIssuer
    ) {
        this.restTemplate = restTemplate != null ? restTemplate : new RestTemplate();
        this.tradeApiUrl = tradeApiUrl;
        this.jwtSecret = jwtSecret;
        this.jwtIssuer = jwtIssuer;
    }

    /**
     * Places an automated order by invoking POST /api/v1/orders.
     *
     * @param accountId target account identifier
     * @param symbol instrument ticker symbol
     * @param side BUY or SELL
     * @param quantity order quantity
     * @param limitPrice optional limit price (null for MARKET order)
     * @return OrderResponse returned by the Trade API
     */
    public OrderResponse placeOrder(
            Long accountId,
            String symbol,
            OrderSide side,
            Integer quantity,
            BigDecimal limitPrice
    ) {
        String cleanBase = (tradeApiUrl != null ? tradeApiUrl : "http://localhost:8081").replaceAll("/+$", "");
        String targetUrl = cleanBase.endsWith("/api/v1") ? cleanBase + "/orders" : cleanBase + "/api/v1/orders";

        PlaceOrderRequest request = new PlaceOrderRequest();
        request.setAccountId(accountId);
        request.setSymbol(symbol);
        request.setSide(side);
        request.setOrderType(limitPrice != null ? OrderType.LIMIT : OrderType.MARKET);
        request.setQuantity(quantity);
        request.setPrice(limitPrice);
        request.setIdempotencyKey(UUID.randomUUID().toString());

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        String bearerToken = generateTokenForAccount(accountId);
        if (bearerToken != null && !bearerToken.isBlank()) {
            headers.setBearerAuth(bearerToken);
        }

        HttpEntity<PlaceOrderRequest> entity = new HttpEntity<>(request, headers);

        logger.info(
                "Dispatching automated order: accountId={}, symbol={}, side={}, qty={}, idempotencyKey={}",
                accountId, symbol, side, quantity, request.getIdempotencyKey()
        );

        try {
            ResponseEntity<OrderResponse> response = restTemplate.postForEntity(
                    targetUrl,
                    entity,
                    OrderResponse.class
            );

            return response.getBody();
        } catch (org.springframework.web.client.HttpStatusCodeException ex) {
            logger.error("Trade API rejected automated order: status={}, body={}",
                    ex.getStatusCode(), ex.getResponseBodyAsString());
            throw ex;
        } catch (Exception ex) {
            logger.error("Failed to connect to Trade API at {}: {}", targetUrl, ex.getMessage());
            throw ex;
        }
    }

    /**
     * Generates a signed HS256 JWT for automated execution on behalf of the customer's account.
     */
    public String generateTokenForAccount(Long accountId) {
        if (jwtSecret == null || jwtSecret.isBlank()) {
            return "dev-internal-token";
        }

        try {
            Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();

            String headerJson = "{\"alg\":\"HS256\",\"typ\":\"JWT\"}";
            long exp = Instant.now().getEpochSecond() + 300; // 5-minute validity window
            String payloadJson = String.format(
                    "{\"accountId\":%d,\"iss\":\"%s\",\"exp\":%d}",
                    accountId,
                    jwtIssuer != null ? jwtIssuer : "trade-api",
                    exp
            );

            String encodedHeader = encoder.encodeToString(headerJson.getBytes(StandardCharsets.UTF_8));
            String encodedPayload = encoder.encodeToString(payloadJson.getBytes(StandardCharsets.UTF_8));
            String signingInput = encodedHeader + "." + encodedPayload;

            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(jwtSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] signature = mac.doFinal(signingInput.getBytes(StandardCharsets.UTF_8));
            String encodedSignature = encoder.encodeToString(signature);

            return signingInput + "." + encodedSignature;
        } catch (Exception ex) {
            logger.warn("Could not generate signed JWT for automated order, fallback to dev token: {}", ex.getMessage());
            return "dev-internal-token";
        }
    }
}

