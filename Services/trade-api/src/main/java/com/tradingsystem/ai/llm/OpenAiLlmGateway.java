package com.tradingsystem.ai.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * OpenAI REST implementation of LlmGateway.
 * Calls the OpenAI Chat Completions endpoint.
 * Activated when llm.provider=openai.
 */
public class OpenAiLlmGateway implements LlmGateway {

    private static final Logger log = LoggerFactory.getLogger(OpenAiLlmGateway.class);

    private final String apiKey;
    private final String model;
    private final String baseUrl;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public OpenAiLlmGateway(
            @Value("${llm.api-key}") String apiKey,
            @Value("${llm.model:gpt-4o-mini}") String model,
            @Value("${llm.base-url:https://api.openai.com/v1}") String baseUrl) {
        this.apiKey = apiKey;
        this.model = model;
        this.baseUrl = baseUrl;
        this.objectMapper = new ObjectMapper();
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .build();
    }

    @Override
    public String sendPrompt(String prompt) {
        try {
            String url = baseUrl + "/chat/completions";

            Map<String, Object> requestBody = Map.of(
                    "model", model,
                    "messages", List.of(Map.of("role", "user", "content", prompt)),
                    "temperature", 0.3,
                    "max_tokens", 512
            );

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(requestBody)))
                    .timeout(Duration.ofSeconds(30))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                log.error("OpenAI API error: status={}, body={}", response.statusCode(), response.body());
                throw new LlmException("OpenAI API returned status " + response.statusCode());
            }

            JsonNode root = objectMapper.readTree(response.body());
            return root.path("choices").get(0).path("message").path("content").asText();

        } catch (LlmException e) {
            throw e;
        } catch (Exception e) {
            log.error("OpenAI LLM call failed", e);
            throw new LlmException("OpenAI LLM call failed: " + e.getMessage(), e);
        }
    }
}
