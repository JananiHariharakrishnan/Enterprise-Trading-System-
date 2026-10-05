package com.tradingsystem.service;

import com.tradingsystem.dto.response.InstrumentLookupResponse;

import java.util.List;

public interface InstrumentService {

    List<InstrumentLookupResponse> listInstruments(String query, Integer limit);
}
