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

class OpenAiLlmGatewayTest {

    @Test
    void sendPrompt_returnsMessageContentAndSendsBearerToken() throws Exception {
        AtomicReference<String> authorization = new AtomicReference<>();
        AtomicReference<String> body = new AtomicReference<>();
        HttpServer server = server("/v1/chat/completions", 200,
                "{\"choices\":[{\"message\":{\"content\":\"buy AAPL\"}}]}", authorization, body);
        try {
            OpenAiLlmGateway gateway = new OpenAiLlmGateway(
                    "test-key", "gpt-test", "http://127.0.0.1:" + server.getAddress().getPort() + "/v1");

            assertEquals("buy AAPL", gateway.sendPrompt("what now"));
            assertEquals("Bearer test-key", authorization.get());
            assertTrue(body.get().contains("\"model\":\"gpt-test\""));
            assertTrue(body.get().contains("what now"));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void sendPrompt_non200_rethrowsLlmException() throws Exception {
        HttpServer server = server("/v1/chat/completions", 503, "{\"error\":\"down\"}", null, null);
        try {
            OpenAiLlmGateway gateway = new OpenAiLlmGateway(
                    "k", "m", "http://127.0.0.1:" + server.getAddress().getPort() + "/v1");
            LlmException ex = assertThrows(LlmException.class, () -> gateway.sendPrompt("hi"));
            assertTrue(ex.getMessage().contains("503"));
            assertEquals(null, ex.getCause());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void sendPrompt_badJsonAndUnreachableHost_wrapFailures() throws Exception {
        HttpServer server = server("/v1/chat/completions", 200, "not-json", null, null);
        try {
            OpenAiLlmGateway gateway = new OpenAiLlmGateway(
                    "k", "m", "http://127.0.0.1:" + server.getAddress().getPort() + "/v1");
            LlmException badJson = assertThrows(LlmException.class, () -> gateway.sendPrompt("hi"));
            assertTrue(badJson.getMessage().contains("OpenAI LLM call failed"));
            assertTrue(badJson.getCause() != null);
        } finally {
            server.stop(0);
        }

        HttpServer emptyChoices = server("/v1/chat/completions", 200, "{\"choices\":[]}", null, null);
        try {
            OpenAiLlmGateway gateway = new OpenAiLlmGateway(
                    "k", "m", "http://127.0.0.1:" + emptyChoices.getAddress().getPort() + "/v1");
            assertThrows(LlmException.class, () -> gateway.sendPrompt("hi"));
        } finally {
            emptyChoices.stop(0);
        }

        OpenAiLlmGateway offline = new OpenAiLlmGateway("k", "m", "http://127.0.0.1:9/v1");
        LlmException offlineError = assertThrows(LlmException.class, () -> offline.sendPrompt("hi"));
        assertTrue(offlineError.getMessage().contains("OpenAI LLM call failed"));
    }

    private static HttpServer server(String path, int status, String response,
                                     AtomicReference<String> authorization,
                                     AtomicReference<String> body) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext(path, exchange -> {
            byte[] request = exchange.getRequestBody().readAllBytes();
            if (authorization != null) {
                authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            }
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
