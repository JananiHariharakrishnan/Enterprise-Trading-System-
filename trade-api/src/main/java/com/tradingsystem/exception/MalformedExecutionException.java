package com.tradingsystem.exception;

/**
 * Thrown when execution cannot proceed due to malformed input or missing required fields.
 * This is a permanent failure that should be sent to dead-letter topic.
 */
public class MalformedExecutionException extends RuntimeException {
    
    public MalformedExecutionException(String message) {
        super(message);
    }
    
    public MalformedExecutionException(String message, Throwable cause) {
        super(message, cause);
    }
}

