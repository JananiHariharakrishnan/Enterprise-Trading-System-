package com.tradingsystem.controller;

import com.tradingsystem.dto.response.LatestMarketPrice;
import com.tradingsystem.service.MarketDataService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class MarketDataControllerTest {

    @Mock
    private MarketDataService marketDataService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new MarketDataController(marketDataService)).build();
    }

    @Test
    void getAllMarketData_emptyCache_returnsEmptyObject() throws Exception {
        when(marketDataService.getAllCachedPrices()).thenReturn(Map.of());

        mockMvc.perform(get("/api/v1/market-data"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void getAllMarketData_mapsQuotesAndSkipsNullFields() throws Exception {
        LatestMarketPrice full = new LatestMarketPrice(
                "AAPL",
                new BigDecimal("232.50"),
                "USD",
                new BigDecimal("1.25"),
                new BigDecimal("0.54"),
                new BigDecimal("231.25"),
                "OPEN",
                Boolean.TRUE,
                "2026-04-01T15:30:00Z"
        );
        LatestMarketPrice bare = new LatestMarketPrice();
        bare.setPrice(new BigDecimal("10"));
        bare.setCurrency("INR");

        Map<String, LatestMarketPrice> cached = new HashMap<>();
        cached.put("AAPL", full);
        cached.put("BARE", bare);
        cached.put("MISSING", null);
        when(marketDataService.getAllCachedPrices()).thenReturn(cached);

        mockMvc.perform(get("/api/v1/market-data"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.AAPL.price").value(232.50))
                .andExpect(jsonPath("$.AAPL.currency").value("USD"))
                .andExpect(jsonPath("$.AAPL.change").value(1.25))
                .andExpect(jsonPath("$.AAPL.changePercent").value(0.54))
                .andExpect(jsonPath("$.AAPL.previousClose").value(231.25))
                .andExpect(jsonPath("$.AAPL.marketState").value("OPEN"))
                .andExpect(jsonPath("$.AAPL.stale").value(true))
                .andExpect(jsonPath("$.AAPL.quoteAsOf").exists())
                .andExpect(jsonPath("$.BARE.price").value(10))
                .andExpect(jsonPath("$.BARE.stale").value(false))
                .andExpect(jsonPath("$.BARE.quoteAsOf").value(nullValue()))
                .andExpect(jsonPath("$.MISSING").value(nullValue()));
    }
}
