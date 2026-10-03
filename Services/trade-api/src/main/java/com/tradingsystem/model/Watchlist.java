package com.tradingsystem.model;

import java.time.OffsetDateTime;

public class Watchlist {

    private Long id;
    private Long accountId;
    private String name;
    private OffsetDateTime createdOn;
    private OffsetDateTime updatedOn;
    private int entryCount;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getAccountId() {
        return accountId;
    }

    public void setAccountId(Long accountId) {
        this.accountId = accountId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
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

    public int getEntryCount() {
        return entryCount;
    }

    public void setEntryCount(int entryCount) {
        this.entryCount = entryCount;
    }

    public void validate() {
        if (accountId == null || accountId <= 0) {
            throw new IllegalArgumentException("accountId required");
        }
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("name required");
        }
    }
}
