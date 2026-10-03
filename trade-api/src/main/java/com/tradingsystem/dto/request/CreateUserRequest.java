package com.tradingsystem.dto.request;

public class CreateUserRequest {
    private String username;
    private Long accountId;
    private String[] roles;

    public CreateUserRequest() {
    }

    public CreateUserRequest(String username, Long accountId, String[] roles) {
        this.username = username;
        this.accountId = accountId;
        this.roles = roles;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public Long getAccountId() {
        return accountId;
    }

    public void setAccountId(Long accountId) {
        this.accountId = accountId;
    }

    public String[] getRoles() {
        return roles;
    }

    public void setRoles(String[] roles) {
        this.roles = roles;
    }
}
