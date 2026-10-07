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
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Gemini REST implementation of LlmGateway.
 * Calls the Gemini generateContent endpoint using the Java HTTP client.
 * Activated when llm.provider=gemini (default).
 *
 * Includes automatic retry and resilient fallback across candidate models
 * to seamlessly absorb transient 503 high-demand spikes.
 */
public class GeminiLlmGateway implements LlmGateway {

    private static final Logger log = LoggerFactory.getLogger(GeminiLlmGateway.class);

    private final String apiKey;
    private final String model;
    private final String baseUrl;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public GeminiLlmGateway(
            @Value("${llm.api-key}") String apiKey,
            @Value("${llm.model:gemini-3.5-flash-lite}") String model,
            @Value("${llm.base-url:https://generativelanguage.googleapis.com/v1beta}") String baseUrl) {
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
        Set<String> candidates = new LinkedHashSet<>();
        if (model != null && !model.isBlank()) {
            candidates.add(model);
        }
        candidates.add("gemini-3.5-flash-lite");
        candidates.add("gemini-flash-lite-latest");
        candidates.add("gemini-3.1-flash-lite");
        candidates.add("gemini-3.8-flash");

        String lastError = null;

        for (String currentModel : candidates) {
            for (int attempt = 1; attempt <= 2; attempt++) {
                try {
                    String result = executeCall(currentModel, prompt);
                    if (result != null && !result.isBlank()) {
                        return result;
                    }
                } catch (LlmException e) {
                    lastError = e.getMessage();
                    log.warn("Gemini model {} attempt {} failed: {}", currentModel, attempt, e.getMessage());
                    if (attempt < 2) {
                        try {
                            Thread.sleep(800);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                        }
                    }
                }
            }
        }

        throw new LlmException("All Gemini models failed. Last error: " + lastError);
    }

    private String executeCall(String targetModel, String prompt) {
        try {
            String url = String.format("%s/models/%s:generateContent?key=%s", baseUrl, targetModel, apiKey);

            String requestBody = objectMapper.writeValueAsString(new java.util.HashMap<String, Object>() {{
                put("contents", new Object[]{
                        new java.util.HashMap<String, Object>() {{
                            put("parts", new Object[]{
                                    new java.util.HashMap<String, Object>() {{
                                        put("text", prompt);
                                    }}
                            });
                        }}
                });
                put("generationConfig", new java.util.HashMap<String, Object>() {{
                    put("temperature", 0.3);
                    put("maxOutputTokens", 512);
                }});
            }});

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .timeout(Duration.ofSeconds(30))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                log.warn("Gemini API error on model {}: status={}, body={}", targetModel, response.statusCode(), response.body());
                throw new LlmException("Gemini API returned status " + response.statusCode());
            }

            JsonNode root = objectMapper.readTree(response.body());
            return root.path("candidates").get(0)
                    .path("content").path("parts").get(0)
                    .path("text").asText();

        } catch (LlmException e) {
            throw e;
        } catch (Exception e) {
            log.error("Gemini call error on model {}: {}", targetModel, e.getMessage());
            throw new LlmException("Gemini call failed for " + targetModel + ": " + e.getMessage(), e);
        }
    }
}
