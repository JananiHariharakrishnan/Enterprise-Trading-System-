package com.tradingsystem.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public class UpdateStatusRequest {
    @NotBlank(message = "Status is required")
    @Pattern(regexp = "^(ACTIVE|SUSPENDED|CLOSED)$", message = "Status must be ACTIVE, SUSPENDED, or CLOSED")
    private String status;

    public UpdateStatusRequest() {
    }

    public UpdateStatusRequest(String status) {
        this.status = status;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
