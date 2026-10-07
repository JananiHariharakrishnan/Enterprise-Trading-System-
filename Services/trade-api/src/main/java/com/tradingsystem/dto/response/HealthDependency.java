package com.tradingsystem.dto.response;

public record HealthDependency(String name, String status, Integer quotaRemaining) {
}
