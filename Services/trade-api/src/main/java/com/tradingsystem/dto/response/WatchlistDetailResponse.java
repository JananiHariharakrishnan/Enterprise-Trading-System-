package com.tradingsystem.dto.response;

import java.time.OffsetDateTime;
import java.util.List;

public class WatchlistDetailResponse {

    private Long id;
    private String name;
    private Integer entryCount;
    private OffsetDateTime createdOn;
    private OffsetDateTime updatedOn;
    private List<WatchlistInstrumentResponse> instruments;

    public WatchlistDetailResponse() {
        // Empty Constructor
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Integer getEntryCount() {
        return entryCount;
    }

    public void setEntryCount(Integer entryCount) {
        this.entryCount = entryCount;
    }

    public OffsetDateTime getCreatedOn() {
        return createdOn;
    }

    public void setCreatedOn(OffsetDateTime createdOn) {
        this.createdOn = createdOn;
    }

    public OffsetDateTime getUpdatedOn() {
        return updatedOn;
    }

    public void setUpdatedOn(OffsetDateTime updatedOn) {
        this.updatedOn = updatedOn;
    }

    public List<WatchlistInstrumentResponse> getInstruments() {
        return instruments;
    }

    public void setInstruments(List<WatchlistInstrumentResponse> instruments) {
        this.instruments = instruments;
    }
}

