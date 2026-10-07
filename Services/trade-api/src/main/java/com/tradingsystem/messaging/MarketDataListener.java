package com.tradingsystem.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradingsystem.automation.service.StrategyAutomationService;
import com.tradingsystem.service.MarketDataService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Component
public class MarketDataListener {

    private static final Logger logger =
            LoggerFactory.getLogger(MarketDataListener.class);

    private static final String EXPECTED_EVENT_TYPE = "QUOTE";
    private static final Integer SUPPORTED_SCHEMA_VERSION = 1;

    private final ObjectMapper objectMapper;
    private final MarketDataService marketDataService;
    private final StrategyAutomationService strategyAutomationService;

    public MarketDataListener(
            ObjectMapper objectMapper,
            MarketDataService marketDataService
    ) {
        this(objectMapper, marketDataService, null);
    }

    @Autowired
    public MarketDataListener(
            ObjectMapper objectMapper,
            MarketDataService marketDataService,
            @Autowired(required = false) StrategyAutomationService strategyAutomationService
    ) {
        this.objectMapper = objectMapper;
        this.marketDataService = marketDataService;
        this.strategyAutomationService = strategyAutomationService;
    }

    @KafkaListener(
            topics = "market-data",
            groupId = "watchlist-service",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void handleMarketData(
            ConsumerRecord<String, String> consumerRecord,
            Acknowledgment acknowledgment
    ) {

        String message = consumerRecord.value();

        try {
            KafkaEventEnvelope<QuotePayload> envelope =
                    objectMapper.readValue(
                            message,
                            new TypeReference<KafkaEventEnvelope<QuotePayload>>() {
                            }
                    );

            processMarketData(envelope);

            acknowledgment.acknowledge();

        } catch (JsonProcessingException exception) {

            logger.error(
                    "Malformed market-data event: {}",
                    exception.getMessage()
            );

            acknowledgment.acknowledge();

        } catch (IllegalArgumentException exception) {

            logger.error(
                    "Malformed market-data event: {}",
                    exception.getMessage()
            );

            acknowledgment.acknowledge();

        } catch (Exception exception) {

            logger.error(
                    "Unexpected error processing market-data event",
                    exception
            );

            throw new RuntimeException(
                    "Failed to process market-data event",
                    exception
            );
        }
    }

    /*
     * Business-level processing.
     *
     * Kept separate from Kafka so unit tests do not need to
     * construct ConsumerRecord/Acknowledgment objects.
     */
    void processMarketData(
            KafkaEventEnvelope<QuotePayload> envelope
    ) {

        if (envelope == null) {
            logger.error(
                    "Received null envelope on market-data topic"
            );
            return;
        }

        String eventId = envelope.getEventId();
        String eventType = envelope.getEventType();
        Integer schemaVersion = envelope.getSchemaVersion();

        if (!EXPECTED_EVENT_TYPE.equals(eventType)) {

            logger.warn(
                    "Unexpected event type on market-data topic: " +
                            "expected '{}', got '{}'. Event ID: {}",
                    EXPECTED_EVENT_TYPE,
                    eventType,
                    eventId
            );

            return;
        }

        if (schemaVersion == null
                || !schemaVersion.equals(SUPPORTED_SCHEMA_VERSION)) {

            logger.warn(
                    "Unsupported schema version on market-data topic: " +
                            "expected {}, got {}. Event ID: {}",
                    SUPPORTED_SCHEMA_VERSION,
                    schemaVersion,
                    eventId
            );

            return;
        }

        QuotePayload payload = envelope.getPayload();

        if (payload == null) {

            logger.error(
                    "Null payload in envelope on market-data topic. " +
                            "Event ID: {}",
                    eventId
            );

            return;
        }

        marketDataService.processQuote(
                eventId,
                eventType,
                payload
        );

        if (strategyAutomationService != null) {
            try {
                strategyAutomationService.onQuote(payload);
            } catch (Exception ex) {
                logger.error("Error evaluating automated strategy for quote: {}", ex.getMessage(), ex);
            }
        }

        logger.debug(
                "Processed market-data event: symbol={}, eventId={}",
                payload.getSymbol(),
                eventId
        );
    }
}