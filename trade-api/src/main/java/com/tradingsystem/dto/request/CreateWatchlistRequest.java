package com.tradingsystem.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class CreateWatchlistRequest {

    @NotBlank
    @Size(min = 1, max = 100)
    private String name;

    public CreateWatchlistRequest() {
        // Empty Constructor
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}

