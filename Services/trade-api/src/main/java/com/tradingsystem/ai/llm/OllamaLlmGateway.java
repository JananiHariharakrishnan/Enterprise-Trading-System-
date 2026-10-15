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
 * Ollama local LLM implementation of LlmGateway.
 * Calls the Ollama /api/generate endpoint (local model server).
 * Activated when llm.provider=ollama.
 */
public class OllamaLlmGateway implements LlmGateway {

    private static final Logger log = LoggerFactory.getLogger(OllamaLlmGateway.class);

    private final String model;
    private final String baseUrl;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public OllamaLlmGateway(
            @Value("${llm.model:llama3}") String model,
            @Value("${llm.base-url:${LLM_BASE_URL:http://localhost:11434}}") String baseUrl) {
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
            String url = baseUrl + "/api/generate";

            Map<String, Object> requestBody = Map.of(
                    "model", model,
                    "prompt", prompt,
                    "stream", false,
                    "options", Map.of("temperature", 0.3, "num_predict", 512)
            );

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(requestBody)))
                    .timeout(Duration.ofSeconds(60))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                log.error("Ollama API error: status={}, body={}", response.statusCode(), response.body());
                throw new LlmException("Ollama API returned status " + response.statusCode());
            }

            JsonNode root = objectMapper.readTree(response.body());
            return root.path("response").asText();

        } catch (LlmException e) {
            throw e;
        } catch (Exception e) {
            log.error("Ollama LLM call failed", e);
            throw new LlmException("Ollama LLM call failed: " + e.getMessage(), e);
        }
    }
}
