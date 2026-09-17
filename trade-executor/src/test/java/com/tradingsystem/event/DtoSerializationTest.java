package com.tradingsystem.event;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradingsystem.event.payload.TradeEventPayload;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class DtoSerializationTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void tradeEventPayload_roundTrip_serializesAndDeserializes() throws Exception {
        TradeEventPayload p = new TradeEventPayload();
        p.setOrderId("o1");
        p.setAccountId(7L);
        p.setSymbol("AAPL");
        p.setQuantity(BigDecimal.TEN);
        p.setPrice(new BigDecimal("100.00"));

        String json = mapper.writeValueAsString(p);
        TradeEventPayload out = mapper.readValue(json, TradeEventPayload.class);

        assertEquals(p.getOrderId(), out.getOrderId());
        assertEquals(p.getAccountId(), out.getAccountId());
        assertEquals(p.getSymbol(), out.getSymbol());
    }

    @Test
    void kafkaEvent_withPayload_roundTrip_preservesPayloadType() throws Exception {
        TradeEventPayload p = new TradeEventPayload();
        p.setOrderId("o2");
        p.setAccountId(9L);

        KafkaEvent<TradeEventPayload> ev = new KafkaEvent<>();
        ev.setEventType("ORDER_FILLED");
        ev.setPayload(p);

        String json = mapper.writeValueAsString(ev);
        KafkaEvent<TradeEventPayload> out = mapper.readValue(json, new TypeReference<KafkaEvent<TradeEventPayload>>(){});

        assertNotNull(out.getPayload());
        assertEquals(p.getOrderId(), out.getPayload().getOrderId());
    }
}
