package com.tradingsystem.controller;

import com.tradingsystem.event.payload.TradeEventPayload;
import com.tradingsystem.producer.TradeEventProducer;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;

@RestController
@RequestMapping("/test/trade-event")
public class TestTradeEventController {

    private final TradeEventProducer tradeEventProducer;

    public TestTradeEventController(TradeEventProducer tradeEventProducer) {
        this.tradeEventProducer = tradeEventProducer;
    }

    @PostMapping("/filled")
    public String publishFilledEvent() {

        TradeEventPayload payload = new TradeEventPayload();

        payload.setOrderId("00000000-0000-0000-0000-000000000001");
        payload.setAccountId(1L);
        payload.setSymbol("AAPL");
        payload.setSide("BUY");
        payload.setQuantity(10);
        payload.setPrice(new BigDecimal("100.00"));
        payload.setExecutedPrice(new BigDecimal("99.50"));
        payload.setStatus("FILLED");
        payload.setReason(null);
        payload.setCashDelta(new BigDecimal("-995.00"));
        payload.setPositionQuantityAfter(20);
        payload.setAverageCostAfter(new BigDecimal("99.75"));
        payload.setExecutedOn("2026-09-16T06:00:00Z");

        tradeEventProducer.publishOrderFilled(payload);

        return "ORDER_FILLED event published";
    }
}
