package com.tradingsystem.controller;

import com.tradingsystem.dto.response.InstrumentLookupResponse;
import com.tradingsystem.service.InstrumentService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/instruments")
public class InstrumentController {

    private final InstrumentService instrumentService;

    public InstrumentController(InstrumentService instrumentService) {
        this.instrumentService = instrumentService;
    }

    @GetMapping
    public ResponseEntity<List<InstrumentLookupResponse>> listInstruments(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) Integer limit
    ) {
        return ResponseEntity.ok(instrumentService.listInstruments(query, limit));
    }
}
