package com.tradingsystem.service.Impl;

import com.tradingsystem.dto.response.InstrumentLookupResponse;
import com.tradingsystem.mapper.InstrumentDataMapper;
import com.tradingsystem.model.Instrument;
import com.tradingsystem.service.InstrumentService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class InstrumentServiceImpl implements InstrumentService {

    private static final int DEFAULT_LIMIT = 75;
    private static final int MAX_LIMIT = 200;

    private final InstrumentDataMapper instrumentDataMapper;

    public InstrumentServiceImpl(InstrumentDataMapper instrumentDataMapper) {
        this.instrumentDataMapper = instrumentDataMapper;
    }

    @Override
    public List<InstrumentLookupResponse> listInstruments(String query, Integer limit) {
        int resolvedLimit = DEFAULT_LIMIT;

        if (limit != null) {
            resolvedLimit = Math.max(1, Math.min(limit, MAX_LIMIT));
        }

        String trimmedQuery = query == null ? null : query.trim();

        List<Instrument> instruments = instrumentDataMapper.findTradableInstruments(trimmedQuery, resolvedLimit);
        List<InstrumentLookupResponse> response = new ArrayList<>();

        for (Instrument instrument : instruments) {
            InstrumentLookupResponse item = new InstrumentLookupResponse();
            item.setSymbol(instrument.getSymbol());
            item.setName(instrument.getName());
            item.setCurrency(instrument.getCurrency());
            item.setAssetClass(
                    instrument.getAssetClass() == null
                            ? null
                            : instrument.getAssetClass().name()
            );
            response.add(item);
        }

        return response;
    }
}
