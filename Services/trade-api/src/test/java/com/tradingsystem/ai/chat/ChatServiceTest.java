package com.tradingsystem.ai.chat;

import com.tradingsystem.ai.llm.LlmGateway;
import com.tradingsystem.ai.signal.TradeSignalStore;
import com.tradingsystem.dto.response.ChatMessageResponse;
import com.tradingsystem.model.AiSignal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ChatServiceTest {

    private ChatContextBuilder contextBuilder;
    private LlmGateway llmGateway;
    private TradeSignalStore signalStore;
    private ChatGuardrailService guardrailService;
    private ChatService chatService;

    @BeforeEach
    void setUp() {
        contextBuilder = Mockito.mock(ChatContextBuilder.class);
        llmGateway = Mockito.mock(LlmGateway.class);
        signalStore = Mockito.mock(TradeSignalStore.class);
        guardrailService = new ChatGuardrailService();

        chatService = new ChatService(contextBuilder, llmGateway, signalStore, guardrailService);
    }

    @Test
    @DisplayName("Autonomous trade request is intercepted without calling LLM and provides signal button")
    void testAutonomousExecutionInterceptedWithSignalCards() {
        AiSignal aaplSignal = new AiSignal();
        aaplSignal.setSymbol("AAPL");
        aaplSignal.setAction("BUY");
        aaplSignal.setConfidence(0.88);
        aaplSignal.setReasoning("Bullish EMA crossover");

        when(signalStore.getBySymbol("AAPL")).thenReturn(Optional.of(aaplSignal));

        ChatMessageResponse response = chatService.chat(101L, "Buy 50 shares of AAPL for me right now");

        // LLM gateway should never be invoked for intercepted execution requests
        verify(llmGateway, never()).sendPrompt(any());
        assertTrue(response.getReply().contains("cannot execute orders"));
        assertTrue(response.getReply().contains("Order Ticket"));

        // Related signal for AAPL is still extracted so user can click 'Trade AAPL'
        assertNotNull(response.getRelatedSignals());
        assertFalse(response.getRelatedSignals().isEmpty());
        assertEquals("AAPL", response.getRelatedSignals().get(0).getSymbol());
    }

    @Test
    @DisplayName("Valid trading inquiry passes guardrails and queries LLM gateway")
    void testValidInquiryCallsLlm() {
        when(contextBuilder.build(eq(101L), anyString())).thenReturn("Enriched prompt");
        when(llmGateway.sendPrompt("Enriched prompt")).thenReturn("AAPL shows bullish momentum with RSI at 62.");

        ChatMessageResponse response = chatService.chat(101L, "What is the RSI trend for AAPL?");

        verify(llmGateway, times(1)).sendPrompt("Enriched prompt");
        assertTrue(response.getReply().contains("AAPL shows bullish momentum"));
    }
}
