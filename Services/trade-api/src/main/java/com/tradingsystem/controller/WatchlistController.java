package com.tradingsystem.controller;

import com.tradingsystem.dto.request.AddWatchlistInstrumentRequest;
import com.tradingsystem.dto.request.CreateWatchlistRequest;
import com.tradingsystem.dto.response.WatchlistDetailResponse;
import com.tradingsystem.dto.response.WatchlistInstrumentResponse;
import com.tradingsystem.dto.response.WatchlistSummaryResponse;
import com.tradingsystem.security.CurrentUser;
import com.tradingsystem.service.WatchlistService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/watchlists")
public class WatchlistController {

    private final WatchlistService watchlistService;
    private final CurrentUser currentUser;

    public WatchlistController(WatchlistService watchlistService, CurrentUser currentUser) {
        this.watchlistService = watchlistService;
        this.currentUser = currentUser;
    }

    @PostMapping
    public ResponseEntity<WatchlistSummaryResponse> createWatchlist(
            @Valid @RequestBody CreateWatchlistRequest request
    ) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(watchlistService.createWatchlist(currentUser.getAccountId(), request));
    }

    @GetMapping
    public ResponseEntity<List<WatchlistSummaryResponse>> listWatchlists() {
        return ResponseEntity.ok(
                watchlistService.listWatchlists(currentUser.getAccountId())
        );
    }

    @GetMapping("/{watchlistId}")
    public ResponseEntity<WatchlistDetailResponse> getWatchlist(
            @PathVariable Long watchlistId
    ) {
        return ResponseEntity.ok(
                watchlistService.getWatchlist(currentUser.getAccountId(), watchlistId)
        );
    }

    @DeleteMapping("/{watchlistId}")
    public ResponseEntity<Void> deleteWatchlist(
            @PathVariable Long watchlistId
    ) {
        watchlistService.deleteWatchlist(currentUser.getAccountId(), watchlistId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{watchlistId}/instruments")
    public ResponseEntity<WatchlistInstrumentResponse> addInstrument(
            @PathVariable Long watchlistId,
            @Valid @RequestBody AddWatchlistInstrumentRequest request
    ) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(
                        watchlistService.addInstrument(
                                currentUser.getAccountId(),
                                watchlistId,
                                request
                        )
                );
    }

    @DeleteMapping("/{watchlistId}/instruments/{symbol:.+}")
    public ResponseEntity<Void> removeInstrument(
            @PathVariable Long watchlistId,
            @PathVariable String symbol
    ) {
        watchlistService.removeInstrument(currentUser.getAccountId(), watchlistId, symbol);
        return ResponseEntity.noContent().build();
    }
}
