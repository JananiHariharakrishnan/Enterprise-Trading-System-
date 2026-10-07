package com.tradingsystem.ai.llm;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OllamaLlmGatewayTest {

    @Test
    void sendPrompt_returnsResponseField() throws Exception {
        AtomicReference<String> body = new AtomicReference<>();
        HttpServer server = server(200, "{\"response\":\"hold cash\"}", body);
        try {
            OllamaLlmGateway gateway = new OllamaLlmGateway(
                    "llama-test", "http://127.0.0.1:" + server.getAddress().getPort());

            assertEquals("hold cash", gateway.sendPrompt("status?"));
            assertTrue(body.get().contains("\"model\":\"llama-test\""));
            assertTrue(body.get().contains("status?"));
            assertTrue(body.get().contains("\"stream\":false"));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void sendPrompt_non200_rethrowsLlmException() throws Exception {
        HttpServer server = server(500, "{\"error\":\"boom\"}", null);
        try {
            OllamaLlmGateway gateway = new OllamaLlmGateway(
                    "m", "http://127.0.0.1:" + server.getAddress().getPort());
            LlmException ex = assertThrows(LlmException.class, () -> gateway.sendPrompt("hi"));
            assertTrue(ex.getMessage().contains("500"));
            assertEquals(null, ex.getCause());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void sendPrompt_badJsonMissingResponseAndOffline_wrapFailures() throws Exception {
        HttpServer bad = server(200, "{", null);
        try {
            OllamaLlmGateway gateway = new OllamaLlmGateway(
                    "m", "http://127.0.0.1:" + bad.getAddress().getPort());
            LlmException ex = assertThrows(LlmException.class, () -> gateway.sendPrompt("hi"));
            assertTrue(ex.getMessage().contains("Ollama LLM call failed"));
            assertTrue(ex.getCause() != null);
        } finally {
            bad.stop(0);
        }

        HttpServer empty = server(200, "{}", null);
        try {
            OllamaLlmGateway gateway = new OllamaLlmGateway(
                    "m", "http://127.0.0.1:" + empty.getAddress().getPort());
            assertEquals("", gateway.sendPrompt("hi"));
        } finally {
            empty.stop(0);
        }

        OllamaLlmGateway offline = new OllamaLlmGateway("m", "http://127.0.0.1:9");
        assertThrows(LlmException.class, () -> offline.sendPrompt("hi"));
    }

    private static HttpServer server(int status, String response, AtomicReference<String> body) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/generate", exchange -> {
            byte[] request = exchange.getRequestBody().readAllBytes();
            if (body != null) {
                body.set(new String(request, StandardCharsets.UTF_8));
            }
            byte[] payload = response.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, payload.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(payload);
            }
        });
        server.start();
        return server;
    }
}
