package com.tradingsystem.ai.chat;

import com.tradingsystem.ai.llm.LlmGateway;
import com.tradingsystem.ai.signal.PortfolioContext;
import com.tradingsystem.ai.signal.SignalEngine;
import com.tradingsystem.ai.signal.TradeSignalStore;
import com.tradingsystem.dto.response.ChatMessageResponse;
import com.tradingsystem.mapper.OrderDataMapper;
import com.tradingsystem.model.Account;
import com.tradingsystem.model.AiSignal;
import com.tradingsystem.model.Instrument;
import com.tradingsystem.model.Position;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatServiceGapsTest {

    @Mock
    private ChatContextBuilder contextBuilder;
    @Mock
    private LlmGateway llmGateway;
    @Mock
    private TradeSignalStore signalStore;
    @Mock
    private SignalEngine signalEngine;
    @Mock
    private OrderDataMapper orderDataMapper;

    private ChatService chatService;

    @BeforeEach
    void setUp() {
        chatService = new ChatService(
                contextBuilder,
                llmGateway,
                signalStore,
                new ChatGuardrailService()
        );
    }

    @Test
    void chat_preGeneratesMissingSignalUsingPortfolioContext() {
        ReflectionTestUtils.setField(chatService, "signalEngine", signalEngine);
        ReflectionTestUtils.setField(chatService, "orderDataMapper", orderDataMapper);

        Account account = new Account();
        account.setAccountId(44L);
        account.setCashBalance(new BigDecimal("5000"));
        account.setBuyingPower(new BigDecimal("4200"));

        Position position = new Position();
        position.setQuantity(12);
        position.setAverageCost(new BigDecimal("150.25"));

        AiSignal generated = signal("AAPL", "BUY", 0.91);

        when(orderDataMapper.findInstrumentBySymbol("AAPL")).thenReturn(new Instrument());
        when(orderDataMapper.findAccountById(44L)).thenReturn(account);
        when(orderDataMapper.findPosition(44L, "AAPL")).thenReturn(position);
        when(signalStore.getBySymbol("AAPL")).thenReturn(Optional.empty(), Optional.of(generated));
        when(contextBuilder.build(eq(44L), anyString())).thenReturn("prompt");
        when(llmGateway.sendPrompt("prompt")).thenReturn("AAPL remains constructive.");

        ChatMessageResponse response = chatService.chat(44L, "What about AAPL and AAPL?");

        ArgumentCaptor<PortfolioContext> portfolio = ArgumentCaptor.forClass(PortfolioContext.class);
        verify(signalEngine).generateSignalForSymbol(eq("AAPL"), isNull(), portfolio.capture(), eq(true));
        assertEquals(44L, portfolio.getValue().getAccountId());
        assertEquals(new BigDecimal("5000"), portfolio.getValue().getCashBalance());
        assertEquals(new BigDecimal("4200"), portfolio.getValue().getBuyingPower());
        assertEquals(12, portfolio.getValue().getCurrentHoldingQuantity());
        assertEquals(new BigDecimal("150.25"), portfolio.getValue().getCurrentAverageCost());

        assertEquals(1, response.getRelatedSignals().size());
        assertEquals("AAPL", response.getRelatedSignals().get(0).getSymbol());
        assertEquals(0.91, response.getRelatedSignals().get(0).getConfidence());
        assertEquals(62.0, response.getRelatedSignals().get(0).getRsi());
        assertTrue(response.getRelatedSignals().get(0).getMethodology().contains("RSI"));
    }

    @Test
    void chat_stillGeneratesSignalWhenPortfolioLookupFails() {
        ReflectionTestUtils.setField(chatService, "signalEngine", signalEngine);
        ReflectionTestUtils.setField(chatService, "orderDataMapper", orderDataMapper);

        when(orderDataMapper.findInstrumentBySymbol("AAPL")).thenThrow(new RuntimeException("lookup failed"));
        when(orderDataMapper.findAccountById(44L)).thenThrow(new RuntimeException("account down"));
        when(signalStore.getBySymbol("AAPL")).thenReturn(Optional.empty());
        when(contextBuilder.build(eq(44L), anyString())).thenReturn("prompt");
        when(llmGateway.sendPrompt("prompt")).thenReturn("No ticker detail.");
        when(signalEngine.generateSignalForSymbol(eq("AAPL"), isNull(), any(), eq(true)))
                .thenThrow(new RuntimeException("engine down"));

        ChatMessageResponse response = chatService.chat(44L, "AAPL outlook");

        verify(signalEngine).generateSignalForSymbol(eq("AAPL"), isNull(), any(PortfolioContext.class), eq(true));
        assertEquals("No ticker detail.", response.getReply());
        assertTrue(response.getRelatedSignals().isEmpty());
    }

    @Test
    void chat_skipsGenerationWhenSignalAlreadyStored() {
        ReflectionTestUtils.setField(chatService, "signalEngine", signalEngine);
        ReflectionTestUtils.setField(chatService, "orderDataMapper", orderDataMapper);

        when(orderDataMapper.findInstrumentBySymbol("MSFT")).thenReturn(new Instrument());
        when(signalStore.getBySymbol("MSFT")).thenReturn(Optional.of(signal("MSFT", "HOLD", 0.74)));
        when(contextBuilder.build(eq(1L), anyString())).thenReturn("prompt");
        when(llmGateway.sendPrompt("prompt")).thenReturn("MSFT is range bound.");

        ChatMessageResponse response = chatService.chat(1L, "MSFT please");

        verify(signalEngine, never()).generateSignalForSymbol(any(), any(), any(), anyBoolean());
        assertEquals("MSFT", response.getRelatedSignals().get(0).getSymbol());
        assertEquals("HOLD", response.getRelatedSignals().get(0).getAction());
    }

    @Test
    void chat_usesReferenceSymbolsWhenMapperIsAbsent() {
        ReflectionTestUtils.setField(chatService, "signalEngine", signalEngine);

        when(signalStore.getBySymbol("SPY")).thenReturn(Optional.empty(), Optional.of(signal("SPY", "SELL", 0.8)));
        when(contextBuilder.build(isNull(), anyString())).thenReturn("prompt");
        when(llmGateway.sendPrompt("prompt")).thenReturn("SPY looks extended.");

        chatService.chat(null, "SPY trend");

        verify(signalEngine).generateSignalForSymbol(eq("SPY"), isNull(), isNull(), eq(true));
    }

    @Test
    void chat_ignoresUnknownTickersAndShortSymbols() {
        ReflectionTestUtils.setField(chatService, "signalEngine", signalEngine);
        ReflectionTestUtils.setField(chatService, "orderDataMapper", orderDataMapper);

        AiSignal queued = signal("Q", "BUY", 0.7);
        when(orderDataMapper.findInstrumentBySymbol("ZZZZ")).thenReturn(null);
        when(orderDataMapper.findInstrumentBySymbol("Q")).thenReturn(new Instrument());
        when(signalStore.getBySymbol("Q")).thenReturn(Optional.of(queued));
        when(contextBuilder.build(eq(3L), anyString())).thenReturn("prompt");
        when(llmGateway.sendPrompt("prompt")).thenReturn("ZZZZ and Q noted.");

        ChatMessageResponse response = chatService.chat(3L, "ZZZZ Q");

        verify(signalEngine, never()).generateSignalForSymbol(any(), any(), any(), anyBoolean());
        assertEquals(1, response.getRelatedSignals().size());
        assertEquals("Q", response.getRelatedSignals().get(0).getSymbol());
    }

    @Test
    void chat_returnsFallbackWhenLlmFailsAndIgnoresBlankInput() {
        when(contextBuilder.build(eq(5L), anyString())).thenReturn("prompt");
        when(llmGateway.sendPrompt("prompt")).thenThrow(new RuntimeException("timeout"));

        ChatMessageResponse failed = chatService.chat(5L, "Explain EMA for INFY.NS");
        assertTrue(failed.getReply().contains("unable to process your request"));

        ChatMessageResponse blank = chatService.chat(5L, "   ");
        assertTrue(blank.getRelatedSignals().isEmpty());
        verify(llmGateway).sendPrompt("prompt");
    }

    private static AiSignal signal(String symbol, String action, double confidence) {
        AiSignal signal = new AiSignal(symbol, action, confidence, "trend", 62.0, 10.0, 9.0, 100.0);
        signal.setGeneratedAt(Instant.parse("2026-01-01T00:00:00Z"));
        return signal;
    }
}
