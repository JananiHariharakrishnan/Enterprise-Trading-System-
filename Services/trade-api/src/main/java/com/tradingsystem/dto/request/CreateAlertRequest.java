package com.tradingsystem.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;

public class CreateAlertRequest {

    @NotBlank
    @Pattern(regexp = "^[A-Z0-9.]{1,20}$", message = "Invalid symbol format")
    private String symbol;

    @NotNull
    @Pattern(regexp = "^(ABOVE|BELOW)$", message = "Direction must be ABOVE or BELOW")
    private String direction;

    @NotNull
    @DecimalMin(value = "0.01", message = "Threshold must be greater than 0")
    private BigDecimal threshold;

    public CreateAlertRequest() {
        // Empty constructor
    }

    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    public String getDirection() {
        return direction;
    }

    public void setDirection(String direction) {
        this.direction = direction;
    }

    public BigDecimal getThreshold() {
        return threshold;
    }

    public void setThreshold(BigDecimal threshold) {
        this.threshold = threshold;
    }
}
