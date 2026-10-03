package com.tradingsystem.exception;

/**
 * Thrown when optimistic lock acquisition fails after maximum retry attempts.
 * This is a permanent failure that should be sent to dead-letter topic.
 */
public class OptimisticLockException extends RuntimeException {
    
    public OptimisticLockException(String message) {
        super(message);
    }
    
    public OptimisticLockException(String message, Throwable cause) {
        super(message, cause);
    }
}

