package com.tradingsystem.controller;

import com.tradingsystem.advice.GlobalExceptionHandler;
import com.tradingsystem.dto.response.LatestPriceResponse;
import com.tradingsystem.dto.response.WatchlistDetailResponse;
import com.tradingsystem.dto.response.WatchlistInstrumentResponse;
import com.tradingsystem.dto.response.WatchlistSummaryResponse;
import com.tradingsystem.exception.WatchlistNotFoundException;
import com.tradingsystem.security.CurrentUser;
import com.tradingsystem.service.WatchlistService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class WatchlistControllerTest {

    private MockMvc mockMvc;

    @Mock
    private WatchlistService watchlistService;

    @Mock
    private CurrentUser currentUser;

    @InjectMocks
    private WatchlistController controller;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        // Mock CurrentUser to return account ID 1001L using lenient() to avoid
        // "unnecessary stubbings" errors when some tests don't use currentUser
        lenient().when(currentUser.getAccountId()).thenReturn(1001L);

        mockMvc = MockMvcBuilders
                .standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();
    }

    @Test
    void createWatchlist_validRequest_returnsCreated() throws Exception {
        WatchlistSummaryResponse response = new WatchlistSummaryResponse();
        response.setId(10L);
        response.setName("Tech");
        response.setEntryCount(0);

        when(watchlistService.createWatchlist(eq(1001L), any())).thenReturn(response);

        mockMvc.perform(post("/api/v1/watchlists")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Tech\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.name").value("Tech"))
                .andExpect(jsonPath("$.entryCount").value(0));
    }

    @Test
    void createWatchlist_invalidRequest_returns422() throws Exception {
        mockMvc.perform(post("/api/v1/watchlists")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"   \"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("VAL-422"));
    }

    @Test
    void listWatchlists_returnsOk() throws Exception {
        WatchlistSummaryResponse response = new WatchlistSummaryResponse();
        response.setId(10L);
        response.setName("Tech");
        response.setEntryCount(2);

        when(watchlistService.listWatchlists(1001L)).thenReturn(List.of(response));

        mockMvc.perform(get("/api/v1/watchlists"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Tech"))
                .andExpect(jsonPath("$[0].entryCount").value(2));
    }

    @Test
    void getWatchlist_returnsOk() throws Exception {
        WatchlistDetailResponse response = new WatchlistDetailResponse();
        response.setId(10L);
        response.setName("Tech");
        response.setEntryCount(1);

        WatchlistInstrumentResponse instrument = new WatchlistInstrumentResponse();
        instrument.setSymbol("AAPL");
        instrument.setAddedOn(OffsetDateTime.parse("2026-09-29T10:15:30Z"));

        LatestPriceResponse latestPrice = new LatestPriceResponse();
        latestPrice.setPrice(new BigDecimal("232.71"));
        latestPrice.setCurrency("USD");
        latestPrice.setMarketState("OPEN");
        instrument.setLatestPrice(latestPrice);

        response.setInstruments(List.of(instrument));

        when(watchlistService.getWatchlist(1001L, 10L)).thenReturn(response);

        mockMvc.perform(get("/api/v1/watchlists/10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Tech"))
                .andExpect(jsonPath("$.instruments[0].symbol").value("AAPL"))
                .andExpect(jsonPath("$.instruments[0].latestPrice.price").value(232.71));
    }

    @Test
    void getWatchlist_notFound_returns404() throws Exception {
        when(watchlistService.getWatchlist(1001L, 999L))
                .thenThrow(new WatchlistNotFoundException());

        mockMvc.perform(get("/api/v1/watchlists/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("WAT-404"));
    }

    @Test
    void deleteWatchlist_returnsNoContent() throws Exception {
        mockMvc.perform(delete("/api/v1/watchlists/10"))
                .andExpect(status().isNoContent());
    }

    @Test
    void addInstrument_returnsCreated() throws Exception {
        WatchlistInstrumentResponse response = new WatchlistInstrumentResponse();
        response.setSymbol("AAPL");

        LatestPriceResponse latestPrice = new LatestPriceResponse();
        latestPrice.setPrice(new BigDecimal("232.71"));
        latestPrice.setCurrency("USD");
        response.setLatestPrice(latestPrice);

        when(watchlistService.addInstrument(eq(1001L), eq(10L), any())).thenReturn(response);

        mockMvc.perform(post("/api/v1/watchlists/10/instruments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"symbol\":\"AAPL\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.symbol").value("AAPL"))
                .andExpect(jsonPath("$.latestPrice.price").value(232.71));
    }

    @Test
    void removeInstrument_returnsNoContent() throws Exception {
        mockMvc.perform(delete("/api/v1/watchlists/10/instruments/INFY.NS"))
                .andExpect(status().isNoContent());
    }
}


