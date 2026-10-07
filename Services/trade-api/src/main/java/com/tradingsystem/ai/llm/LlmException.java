package com.tradingsystem.ai.llm;

/**
 * Runtime exception thrown when an LLM gateway call fails.
 */
public class LlmException extends RuntimeException {

    public LlmException(String message) {
        super(message);
    }

    public LlmException(String message, Throwable cause) {
        super(message, cause);
    }
}
