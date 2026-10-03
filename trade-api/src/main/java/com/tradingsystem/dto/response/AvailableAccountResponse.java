package com.tradingsystem.dto.response;

public class AvailableAccountResponse {
    private Long accountId;

    public AvailableAccountResponse() {
    }

    public AvailableAccountResponse(Long accountId) {
        this.accountId = accountId;
    }

    public Long getAccountId() {
        return accountId;
    }

    public void setAccountId(Long accountId) {
        this.accountId = accountId;
    }
}
