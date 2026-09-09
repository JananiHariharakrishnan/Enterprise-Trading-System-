package com.tradingsystem.model;

public class OrderResult {

    private final boolean success;
    private final String errorCode;

    private OrderResult(boolean success, String errorCode) {
        this.success = success;
        this.errorCode = errorCode;
    }

    public static OrderResult success() {
        return new OrderResult(true, null);
    }

    public static OrderResult failure(String errorCode) {
        return new OrderResult(false, errorCode);
    }

    public boolean isSuccess() {
        return success;
    }

    public String getErrorCode() {
        return errorCode;
    }
}