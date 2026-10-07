package com.tradingsystem.dto.response;

import java.util.List;

public record HealthResponse(String status, List<HealthDependency> dependencies, String asOf) {
}
