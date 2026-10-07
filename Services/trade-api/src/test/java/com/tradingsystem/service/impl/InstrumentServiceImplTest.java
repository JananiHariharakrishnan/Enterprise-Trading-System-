package com.tradingsystem.service.impl;

import com.tradingsystem.dto.response.InstrumentLookupResponse;
import com.tradingsystem.service.Impl.InstrumentServiceImpl;
import com.tradingsystem.enums.AssetClass;
import com.tradingsystem.mapper.InstrumentDataMapper;
import com.tradingsystem.model.Instrument;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InstrumentServiceImplTest {

    @Mock
    private InstrumentDataMapper instrumentDataMapper;

    @InjectMocks
    private InstrumentServiceImpl service;

    @Test
    void listInstruments_nullQueryAndLimit_usesDefaults() {
        when(instrumentDataMapper.findTradableInstruments(null, 75)).thenReturn(List.of());

        List<InstrumentLookupResponse> result = service.listInstruments(null, null);

        assertEquals(List.of(), result);
        verify(instrumentDataMapper).findTradableInstruments(null, 75);
    }

    @Test
    void listInstruments_trimsQueryAndClampsLimit() {
        Instrument equity = instrument("TCS", "Tata", AssetClass.EQUITY, "INR");
        Instrument untyped = instrument("CASH", "Cash", null, "USD");
        when(instrumentDataMapper.findTradableInstruments("tcs", 1)).thenReturn(List.of(equity, untyped));

        List<InstrumentLookupResponse> low = service.listInstruments("  tcs  ", 0);
        assertEquals(2, low.size());
        assertEquals("TCS", low.get(0).getSymbol());
        assertEquals("Tata", low.get(0).getName());
        assertEquals("INR", low.get(0).getCurrency());
        assertEquals("EQUITY", low.get(0).getAssetClass());
        assertNull(low.get(1).getAssetClass());
        assertEquals("CASH", low.get(1).getSymbol());

        when(instrumentDataMapper.findTradableInstruments("", 1)).thenReturn(List.of());
        assertEquals(List.of(), service.listInstruments("   ", -8));

        when(instrumentDataMapper.findTradableInstruments("ibm", 200)).thenReturn(List.of(equity));
        assertEquals(1, service.listInstruments("ibm", 500).size());

        when(instrumentDataMapper.findTradableInstruments("ibm", 200)).thenReturn(List.of());
        service.listInstruments("ibm", 200);
        verify(instrumentDataMapper, times(2)).findTradableInstruments("ibm", 200);

        when(instrumentDataMapper.findTradableInstruments("ibm", 40)).thenReturn(List.of());
        service.listInstruments("ibm", 40);
        verify(instrumentDataMapper).findTradableInstruments("ibm", 40);
    }

    private static Instrument instrument(String symbol, String name, AssetClass assetClass, String currency) {
        Instrument instrument = new Instrument();
        instrument.setSymbol(symbol);
        instrument.setName(name);
        instrument.setAssetClass(assetClass);
        instrument.setCurrency(currency);
        instrument.setTradable(true);
        return instrument;
    }
}
