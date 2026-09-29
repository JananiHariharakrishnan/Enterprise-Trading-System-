package com.tradingsystem.service.Impl;

import com.tradingsystem.dto.request.AddWatchlistInstrumentRequest;
import com.tradingsystem.dto.request.CreateWatchlistRequest;
import com.tradingsystem.dto.response.LatestPriceResponse;
import com.tradingsystem.dto.response.WatchlistDetailResponse;
import com.tradingsystem.dto.response.WatchlistInstrumentResponse;
import com.tradingsystem.dto.response.WatchlistSummaryResponse;
import com.tradingsystem.exception.AccountNotActiveException;
import com.tradingsystem.exception.AccountNotFoundException;
import com.tradingsystem.exception.InstrumentNotFoundException;
import com.tradingsystem.exception.TradingAuthenticationException;
import com.tradingsystem.exception.WatchlistConflictException;
import com.tradingsystem.exception.WatchlistNotFoundException;
import com.tradingsystem.mapper.AccountDataMapper;
import com.tradingsystem.mapper.WatchlistDataMapper;
import com.tradingsystem.model.Account;
import com.tradingsystem.model.Instrument;
import com.tradingsystem.model.Watchlist;
import com.tradingsystem.model.WatchlistEntry;
import com.tradingsystem.security.CurrentUser;
import com.tradingsystem.service.WatchlistService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class WatchlistServiceImpl implements WatchlistService {

    private final WatchlistDataMapper watchlistDataMapper;
    private final AccountDataMapper accountDataMapper;
    private final CurrentUser currentUser;

    public WatchlistServiceImpl(
            WatchlistDataMapper watchlistDataMapper,
            AccountDataMapper accountDataMapper,
            CurrentUser currentUser
    ) {
        this.watchlistDataMapper = watchlistDataMapper;
        this.accountDataMapper = accountDataMapper;
        this.currentUser = currentUser;
    }

    @Override
    @Transactional
    public WatchlistSummaryResponse createWatchlist(
            Long accountId,
            CreateWatchlistRequest request
    ) {
        Account account = getAuthorizedAccount(accountId);

        Watchlist watchlist = new Watchlist();
        watchlist.setAccountId(account.getAccountId());
        watchlist.setName(normalizeName(request.getName()));

        try {
            watchlistDataMapper.insertWatchlist(watchlist);
        } catch (DataIntegrityViolationException exception) {
            throw new WatchlistConflictException();
        }

        Watchlist persisted = watchlistDataMapper.findWatchlistByAccountIdAndId(
                account.getAccountId(),
                watchlist.getId()
        );

        if (persisted == null) {
            throw new IllegalStateException("Created watchlist could not be reloaded");
        }

        return toSummaryResponse(persisted);
    }

    @Override
    public List<WatchlistSummaryResponse> listWatchlists(Long accountId) {
        Account account = getAuthorizedAccount(accountId);

        List<Watchlist> watchlists =
                watchlistDataMapper.findWatchlistsByAccountId(account.getAccountId());

        List<WatchlistSummaryResponse> responses = new ArrayList<>();

        for (Watchlist watchlist : watchlists) {
            responses.add(toSummaryResponse(watchlist));
        }

        return responses;
    }

    @Override
    public WatchlistDetailResponse getWatchlist(
            Long accountId,
            Long watchlistId
    ) {
        Account account = getAuthorizedAccount(accountId);
        Watchlist watchlist = getAuthorizedWatchlist(account.getAccountId(), watchlistId);

        List<WatchlistEntry> entries = watchlistDataMapper.findEntriesByWatchlistId(watchlist.getId());
        Map<String, Object> latestPricesBySymbol = loadLatestPrices(entries);

        List<WatchlistInstrumentResponse> instruments = new ArrayList<>();

        for (WatchlistEntry entry : entries) {
            instruments.add(
                    toInstrumentResponse(
                            entry,
                            latestPricesBySymbol.get(entry.getSymbol())
                    )
            );
        }

        WatchlistDetailResponse response = new WatchlistDetailResponse();
        response.setId(watchlist.getId());
        response.setName(watchlist.getName());
        response.setEntryCount(watchlist.getEntryCount());
        response.setCreatedOn(toOffsetDateTime(watchlist.getCreatedOn()));
        response.setUpdatedOn(toOffsetDateTime(watchlist.getUpdatedOn()));
        response.setInstruments(instruments);

        return response;
    }

    @Override
    @Transactional
    public void deleteWatchlist(
            Long accountId,
            Long watchlistId
    ) {
        Account account = getAuthorizedAccount(accountId);

        int deleted = watchlistDataMapper.deleteWatchlist(
                account.getAccountId(),
                watchlistId
        );

        if (deleted == 0) {
            throw new WatchlistNotFoundException();
        }
    }

    @Override
    @Transactional
    public WatchlistInstrumentResponse addInstrument(
            Long accountId,
            Long watchlistId,
            AddWatchlistInstrumentRequest request
    ) {
        Account account = getAuthorizedAccount(accountId);
        Watchlist watchlist = getAuthorizedWatchlist(account.getAccountId(), watchlistId);

        String symbol = normalizeSymbol(request.getSymbol());
        Instrument instrument = watchlistDataMapper.findInstrumentBySymbol(symbol);

        if (instrument == null) {
            throw new InstrumentNotFoundException();
        }

        WatchlistEntry watchlistEntry = new WatchlistEntry();
        watchlistEntry.setWatchlistId(watchlist.getId());
        watchlistEntry.setSymbol(symbol);

        try {
            watchlistDataMapper.insertWatchlistEntry(watchlistEntry);
        } catch (DataIntegrityViolationException exception) {
            throw new WatchlistConflictException("Instrument already in watchlist");
        }

        WatchlistEntry persisted = null;
        List<WatchlistEntry> entries = watchlistDataMapper.findEntriesByWatchlistId(watchlist.getId());

        for (WatchlistEntry entry : entries) {
            if (symbol.equals(entry.getSymbol())) {
                persisted = entry;
                break;
            }
        }

        if (persisted == null) {
            throw new IllegalStateException("Created watchlist instrument could not be reloaded");
        }

        return toInstrumentResponse(
                persisted,
                null  // Price will come from cache when Kafka consumer is implemented
        );
    }

    @Override
    @Transactional
    public void removeInstrument(
            Long accountId,
            Long watchlistId,
            String symbol
    ) {
        Account account = getAuthorizedAccount(accountId);
        Watchlist watchlist = getAuthorizedWatchlist(account.getAccountId(), watchlistId);

        int deleted = watchlistDataMapper.deleteWatchlistEntry(
                watchlist.getId(),
                normalizeSymbol(symbol)
        );

        if (deleted == 0) {
            throw new WatchlistNotFoundException("Watchlist instrument not found");
        }
    }

    private Account getAuthorizedAccount(Long accountId) {
        if (accountId == null || accountId <= 0) {
            throw new AccountNotFoundException();
        }

        Account account = accountDataMapper.findAccountById(accountId);

        if (account == null) {
            throw new AccountNotFoundException();
        }

        if (!account.isActive()) {
            throw new AccountNotActiveException();
        }

        Long authenticatedAccountId = currentUser.getAccountId();

        if (authenticatedAccountId == null) {
            throw new TradingAuthenticationException();
        }

        if (!accountId.equals(authenticatedAccountId)) {
            throw new AccountNotActiveException();
        }

        return account;
    }

    private Watchlist getAuthorizedWatchlist(
            Long accountId,
            Long watchlistId
    ) {
        if (watchlistId == null || watchlistId <= 0) {
            throw new WatchlistNotFoundException();
        }

        Watchlist watchlist = watchlistDataMapper.findWatchlistByAccountIdAndId(
                accountId,
                watchlistId
        );

        if (watchlist == null) {
            throw new WatchlistNotFoundException();
        }

        return watchlist;
    }

    private Map<String, Object> loadLatestPrices(
            List<WatchlistEntry> entries
    ) {
        // Prices are cached in-memory by Kafka consumer.
        // For now, return empty map. When Kafka consumer is implemented,
        // inject a market price cache service here to look up prices.
        return new HashMap<>();
    }

    private WatchlistSummaryResponse toSummaryResponse(Watchlist watchlist) {
        WatchlistSummaryResponse response = new WatchlistSummaryResponse();
        response.setId(watchlist.getId());
        response.setName(watchlist.getName());
        response.setEntryCount(watchlist.getEntryCount());
        response.setCreatedOn(toOffsetDateTime(watchlist.getCreatedOn()));
        response.setUpdatedOn(toOffsetDateTime(watchlist.getUpdatedOn()));
        return response;
    }

    private WatchlistInstrumentResponse toInstrumentResponse(
            WatchlistEntry entry,
            Object latestMarketPrice
    ) {
        WatchlistInstrumentResponse response = new WatchlistInstrumentResponse();
        response.setSymbol(entry.getSymbol());
        response.setAddedOn(toOffsetDateTime(entry.getAddedOn()));
        response.setLatestPrice(null);  // Will be populated from cache when available
        return response;
    }

    private LatestPriceResponse toLatestPriceResponse(
            Object latestMarketPrice
    ) {
        return null;  // Will be populated from cache when Kafka consumer is implemented
    }

    private OffsetDateTime toOffsetDateTime(OffsetDateTime value) {
        // Already an OffsetDateTime from the database (TIMESTAMPTZ)
        return value;
    }

    private String normalizeName(String name) {
        return name == null ? null : name.trim();
    }

    private String normalizeSymbol(String symbol) {
        return symbol == null ? null : symbol.trim();
    }
}

