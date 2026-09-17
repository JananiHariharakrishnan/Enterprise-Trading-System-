package com.tradingsystem.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Base Kafka event envelope containing metadata common to all events.
 * Follows the contract defined in kafka-topics.md
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class KafkaEvent<T> {
    @JsonProperty("eventId")
    private String eventId;

    @JsonProperty("eventType")
    private String eventType;

    @JsonProperty("eventTime")
    private String eventTime;

    @JsonProperty("source")
    private String source;

    @JsonProperty("schemaVersion")
    private Integer schemaVersion;

    @JsonProperty("payload")
    private T payload;

    public KafkaEvent() {}

    public KafkaEvent(String eventId, String eventType, String eventTime, String source, Integer schemaVersion, T payload) {
        this.eventId = eventId;
        this.eventType = eventType;
        this.eventTime = eventTime;
        this.source = source;
        this.schemaVersion = schemaVersion;
        this.payload = payload;
    }

    // Getters and Setters
    public String getEventId() { return eventId; }
    public void setEventId(String eventId) { this.eventId = eventId; }

    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }

    public String getEventTime() { return eventTime; }
    public void setEventTime(String eventTime) { this.eventTime = eventTime; }

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }

    public Integer getSchemaVersion() { return schemaVersion; }
    public void setSchemaVersion(Integer schemaVersion) { this.schemaVersion = schemaVersion; }

    public T getPayload() { return payload; }
    public void setPayload(T payload) { this.payload = payload; }

}


