package com.tradingsystem.ai.config;

import com.tradingsystem.ai.llm.GeminiLlmGateway;
import com.tradingsystem.ai.llm.LlmGateway;
import com.tradingsystem.ai.llm.OllamaLlmGateway;
import com.tradingsystem.ai.llm.OpenAiLlmGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Selects and wires the correct LlmGateway implementation based on
 * the llm.provider configuration property.
 * Supported values: gemini (default), openai, ollama.
 */
@Configuration
public class LlmConfig {

    private static final Logger log = LoggerFactory.getLogger(LlmConfig.class);

    @Value("${llm.provider:gemini}")
    private String provider;

    @Value("${llm.api-key:}")
    private String apiKey;

    @Value("${llm.model:gemini-3.5-flash-lite}")
    private String model;

    @Value("${llm.base-url:https://generativelanguage.googleapis.com/v1beta}")
    private String baseUrl;

    @Bean
    public LlmGateway llmGateway() {
        log.info("Configuring LLM gateway with provider={}, model={}", provider, model);
        return switch (provider.toLowerCase().trim()) {
            case "openai" -> new OpenAiLlmGateway(apiKey, model, baseUrl);
            case "ollama" -> new OllamaLlmGateway(model, baseUrl);
            default -> {
                log.info("Using Gemini as LLM provider");
                yield new GeminiLlmGateway(apiKey, model, baseUrl);
            }
        };
    }
}
