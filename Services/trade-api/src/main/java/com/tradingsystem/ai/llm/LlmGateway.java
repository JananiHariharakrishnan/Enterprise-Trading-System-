package com.tradingsystem.ai.llm;

/**
 * Provider-agnostic LLM gateway interface.
 * Implementations: GeminiLlmGateway, OpenAiLlmGateway, OllamaLlmGateway.
 * Selected at runtime via LlmConfig based on llm.provider property.
 */
public interface LlmGateway {

    /**
     * Send a prompt to the configured LLM and return the raw text response.
     *
     * @param prompt the full prompt string to send
     * @return the raw text response from the LLM
     * @throws LlmException if the call fails or times out
     */
    String sendPrompt(String prompt);
}
