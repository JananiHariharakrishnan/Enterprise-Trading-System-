package com.tradingsystem.messaging;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record OrderPlacedEvent(
        UUID orderId,
        Long accountId,
        String symbol,
        String side,
        String orderType,
        BigDecimal quantity,
        BigDecimal price,
        String status,
        String idempotencyKey,
        OffsetDateTime createdOn
) {
}