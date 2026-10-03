package com.tradingsystem.service;

import com.tradingsystem.dto.request.AddWatchlistInstrumentRequest;
import com.tradingsystem.dto.request.CreateWatchlistRequest;
import com.tradingsystem.dto.response.WatchlistDetailResponse;
import com.tradingsystem.dto.response.WatchlistInstrumentResponse;
import com.tradingsystem.dto.response.WatchlistSummaryResponse;

import java.util.List;

public interface WatchlistService {

    WatchlistSummaryResponse createWatchlist(
            Long accountId,
            CreateWatchlistRequest request
    );

    List<WatchlistSummaryResponse> listWatchlists(Long accountId);

    WatchlistDetailResponse getWatchlist(
            Long accountId,
            Long watchlistId
    );

    void deleteWatchlist(
            Long accountId,
            Long watchlistId
    );

    WatchlistInstrumentResponse addInstrument(
            Long accountId,
            Long watchlistId,
            AddWatchlistInstrumentRequest request
    );

    void removeInstrument(
            Long accountId,
            Long watchlistId,
            String symbol
    );
}

