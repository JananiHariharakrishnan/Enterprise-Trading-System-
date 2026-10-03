package com.tradingsystem.controller;

import com.tradingsystem.dto.response.InstrumentLookupResponse;
import com.tradingsystem.service.InstrumentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class InstrumentControllerTest {

    private MockMvc mockMvc;

    @Mock
    private InstrumentService instrumentService;

    @InjectMocks
    private InstrumentController controller;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(controller)
                .build();
    }

    @Test
    void listInstruments_returnsRows() throws Exception {
        InstrumentLookupResponse row = new InstrumentLookupResponse();
        row.setSymbol("AAPL");
        row.setName("Apple Inc.");
        row.setAssetClass("EQUITY");
        row.setCurrency("USD");

        when(instrumentService.listInstruments(null, null))
                .thenReturn(List.of(row));

        mockMvc.perform(get("/api/v1/instruments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].symbol").value("AAPL"))
                .andExpect(jsonPath("$[0].name").value("Apple Inc."))
                .andExpect(jsonPath("$[0].assetClass").value("EQUITY"))
                .andExpect(jsonPath("$[0].currency").value("USD"));
    }

    @Test
    void listInstruments_passesQueryAndLimit() throws Exception {
        when(instrumentService.listInstruments("inf", 25))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/v1/instruments")
                        .param("query", "inf")
                        .param("limit", "25"))
                .andExpect(status().isOk());

        verify(instrumentService).listInstruments("inf", 25);
    }
}
