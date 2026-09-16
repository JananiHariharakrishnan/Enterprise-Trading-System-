package com.tradingsystem.messaging;

import java.time.OffsetDateTime;
import java.util.UUID;

public record KafkaEventEnvelope<T>(
        UUID eventId,
        String eventType,
        OffsetDateTime eventTime,
        String source,
        Integer schemaVersion,
        T payload
) {
}