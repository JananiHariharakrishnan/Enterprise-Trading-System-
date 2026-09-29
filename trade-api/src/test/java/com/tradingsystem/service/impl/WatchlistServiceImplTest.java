package com.tradingsystem.service.impl;

import com.tradingsystem.dto.request.AddWatchlistInstrumentRequest;
import com.tradingsystem.dto.request.CreateWatchlistRequest;
import com.tradingsystem.dto.response.WatchlistDetailResponse;
import com.tradingsystem.dto.response.WatchlistInstrumentResponse;
import com.tradingsystem.dto.response.WatchlistSummaryResponse;
import com.tradingsystem.enums.AccountStatus;
import com.tradingsystem.exception.AccountNotActiveException;
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
import com.tradingsystem.service.Impl.WatchlistServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WatchlistServiceImplTest {

    @Mock
    private WatchlistDataMapper watchlistDataMapper;

    @Mock
    private AccountDataMapper accountDataMapper;

    @Mock
    private CurrentUser currentUser;

    private WatchlistServiceImpl watchlistService;

    @BeforeEach
    void setUp() {
        watchlistService = new WatchlistServiceImpl(
                watchlistDataMapper,
                accountDataMapper,
                currentUser
        );
    }

    @Test
    void createWatchlist_returnsCreatedWatchlist_whenAuthorized() {
        Account account = activeAccount(1001L);
        CreateWatchlistRequest request = new CreateWatchlistRequest();
        request.setName("  Tech  ");

        Watchlist persisted = watchlist(10L, 1001L, "Tech", 0);

        when(accountDataMapper.findAccountById(1001L)).thenReturn(account);
        when(currentUser.getAccountId()).thenReturn(1001L);
        doAnswer(invocation -> {
            Watchlist watchlist = invocation.getArgument(0);
            watchlist.setId(10L);
            return 1;
        }).when(watchlistDataMapper).insertWatchlist(any(Watchlist.class));
        when(watchlistDataMapper.findWatchlistByAccountIdAndId(1001L, 10L)).thenReturn(persisted);

        WatchlistSummaryResponse response = watchlistService.createWatchlist(1001L, request);

        assertEquals(10L, response.getId());
        assertEquals("Tech", response.getName());
        assertEquals(0, response.getEntryCount());
        assertNotNull(response.getCreatedOn());
    }

    @Test
    void createWatchlist_throwsConflict_whenDuplicateName() {
        Account account = activeAccount(1001L);
        CreateWatchlistRequest request = new CreateWatchlistRequest();
        request.setName("Tech");

        when(accountDataMapper.findAccountById(1001L)).thenReturn(account);
        when(currentUser.getAccountId()).thenReturn(1001L);
        when(watchlistDataMapper.insertWatchlist(any(Watchlist.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate"));

        assertThrows(
                WatchlistConflictException.class,
                () -> watchlistService.createWatchlist(1001L, request)
        );
    }

    @Test
    void listWatchlists_returnsSummaries_whenAuthorized() {
        Account account = activeAccount(1001L);
        Watchlist tech = watchlist(10L, 1001L, "Tech", 2);
        Watchlist banking = watchlist(11L, 1001L, "Banking", 1);

        when(accountDataMapper.findAccountById(1001L)).thenReturn(account);
        when(currentUser.getAccountId()).thenReturn(1001L);
        when(watchlistDataMapper.findWatchlistsByAccountId(1001L))
                .thenReturn(List.of(tech, banking));

        List<WatchlistSummaryResponse> responses = watchlistService.listWatchlists(1001L);

        assertEquals(2, responses.size());
        assertEquals("Tech", responses.get(0).getName());
        assertEquals(2, responses.get(0).getEntryCount());
        assertEquals("Banking", responses.get(1).getName());
    }

    @Test
    void getWatchlist_returnsEntriesWithoutPrices_pricesWillComeFromCache() {
        Account account = activeAccount(1001L);
        Watchlist watchlist = watchlist(10L, 1001L, "Tech", 2);

        WatchlistEntry aapl = watchlistEntry(10L, "AAPL", LocalDateTime.of(2026, 9, 29, 10, 0));
        WatchlistEntry msft = watchlistEntry(10L, "MSFT", LocalDateTime.of(2026, 9, 29, 10, 5));

        when(accountDataMapper.findAccountById(1001L)).thenReturn(account);
        when(currentUser.getAccountId()).thenReturn(1001L);
        when(watchlistDataMapper.findWatchlistByAccountIdAndId(1001L, 10L)).thenReturn(watchlist);
        when(watchlistDataMapper.findEntriesByWatchlistId(10L)).thenReturn(List.of(aapl, msft));

        WatchlistDetailResponse response = watchlistService.getWatchlist(1001L, 10L);

        assertEquals(10L, response.getId());
        assertEquals("Tech", response.getName());
        assertEquals(2, response.getInstruments().size());
        assertEquals("AAPL", response.getInstruments().get(0).getSymbol());
        assertNull(response.getInstruments().get(0).getLatestPrice());
        assertEquals("MSFT", response.getInstruments().get(1).getSymbol());
        assertNull(response.getInstruments().get(1).getLatestPrice());
    }

    @Test
    void getWatchlist_throwsNotFound_whenMissing() {
        Account account = activeAccount(1001L);

        when(accountDataMapper.findAccountById(1001L)).thenReturn(account);
        when(currentUser.getAccountId()).thenReturn(1001L);
        when(watchlistDataMapper.findWatchlistByAccountIdAndId(1001L, 999L)).thenReturn(null);

        assertThrows(
                WatchlistNotFoundException.class,
                () -> watchlistService.getWatchlist(1001L, 999L)
        );
    }

    @Test
    void deleteWatchlist_throwsNotFound_whenMissing() {
        Account account = activeAccount(1001L);

        when(accountDataMapper.findAccountById(1001L)).thenReturn(account);
        when(currentUser.getAccountId()).thenReturn(1001L);
        when(watchlistDataMapper.deleteWatchlist(1001L, 999L)).thenReturn(0);

        assertThrows(
                WatchlistNotFoundException.class,
                () -> watchlistService.deleteWatchlist(1001L, 999L)
        );
    }

    @Test
    void addInstrument_returnsCreatedInstrumentWithoutPrice_pricesWillComeFromCache() {
        Account account = activeAccount(1001L);
        Watchlist watchlist = watchlist(10L, 1001L, "Tech", 1);
        Instrument instrument = instrument("AAPL");

        AddWatchlistInstrumentRequest request = new AddWatchlistInstrumentRequest();
        request.setSymbol("AAPL");

        WatchlistEntry entry = watchlistEntry(10L, "AAPL", LocalDateTime.of(2026, 9, 29, 10, 15));

        when(accountDataMapper.findAccountById(1001L)).thenReturn(account);
        when(currentUser.getAccountId()).thenReturn(1001L);
        when(watchlistDataMapper.findWatchlistByAccountIdAndId(1001L, 10L)).thenReturn(watchlist);
        when(watchlistDataMapper.findInstrumentBySymbol("AAPL")).thenReturn(instrument);
        when(watchlistDataMapper.findEntriesByWatchlistId(10L)).thenReturn(List.of(entry));

        WatchlistInstrumentResponse response = watchlistService.addInstrument(1001L, 10L, request);

        assertEquals("AAPL", response.getSymbol());
        assertNotNull(response.getAddedOn());
        assertNull(response.getLatestPrice());
    }

    @Test
    void addInstrument_throwsInstrumentNotFound_whenUnknownSymbol() {
        Account account = activeAccount(1001L);
        Watchlist watchlist = watchlist(10L, 1001L, "Tech", 1);

        AddWatchlistInstrumentRequest request = new AddWatchlistInstrumentRequest();
        request.setSymbol("UNKNOWN");

        when(accountDataMapper.findAccountById(1001L)).thenReturn(account);
        when(currentUser.getAccountId()).thenReturn(1001L);
        when(watchlistDataMapper.findWatchlistByAccountIdAndId(1001L, 10L)).thenReturn(watchlist);
        when(watchlistDataMapper.findInstrumentBySymbol("UNKNOWN")).thenReturn(null);

        assertThrows(
                InstrumentNotFoundException.class,
                () -> watchlistService.addInstrument(1001L, 10L, request)
        );
    }

    @Test
    void addInstrument_throwsConflict_whenDuplicateSymbol() {
        Account account = activeAccount(1001L);
        Watchlist watchlist = watchlist(10L, 1001L, "Tech", 1);
        Instrument instrument = instrument("AAPL");

        AddWatchlistInstrumentRequest request = new AddWatchlistInstrumentRequest();
        request.setSymbol("AAPL");

        when(accountDataMapper.findAccountById(1001L)).thenReturn(account);
        when(currentUser.getAccountId()).thenReturn(1001L);
        when(watchlistDataMapper.findWatchlistByAccountIdAndId(1001L, 10L)).thenReturn(watchlist);
        when(watchlistDataMapper.findInstrumentBySymbol("AAPL")).thenReturn(instrument);
        when(watchlistDataMapper.insertWatchlistEntry(any(WatchlistEntry.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate"));

        assertThrows(
                WatchlistConflictException.class,
                () -> watchlistService.addInstrument(1001L, 10L, request)
        );
    }

    @Test
    void removeInstrument_throwsNotFound_whenEntryMissing() {
        Account account = activeAccount(1001L);
        Watchlist watchlist = watchlist(10L, 1001L, "Tech", 1);

        when(accountDataMapper.findAccountById(1001L)).thenReturn(account);
        when(currentUser.getAccountId()).thenReturn(1001L);
        when(watchlistDataMapper.findWatchlistByAccountIdAndId(1001L, 10L)).thenReturn(watchlist);
        when(watchlistDataMapper.deleteWatchlistEntry(10L, "MSFT")).thenReturn(0);

        assertThrows(
                WatchlistNotFoundException.class,
                () -> watchlistService.removeInstrument(1001L, 10L, "MSFT")
        );
    }

    @Test
    void listWatchlists_throwsAcc403_whenTokenCannotReachAccount() {
        Account account = activeAccount(1001L);

        when(accountDataMapper.findAccountById(1001L)).thenReturn(account);
        when(currentUser.getAccountId()).thenReturn(2002L);

        assertThrows(
                AccountNotActiveException.class,
                () -> watchlistService.listWatchlists(1001L)
        );

        verify(watchlistDataMapper, never()).findWatchlistsByAccountId(1001L);
    }

    @Test
    void listWatchlists_throwsAuth401_whenAuthenticationContextMissing() {
        Account account = activeAccount(1001L);

        when(accountDataMapper.findAccountById(1001L)).thenReturn(account);
        when(currentUser.getAccountId()).thenReturn(null);

        assertThrows(
                TradingAuthenticationException.class,
                () -> watchlistService.listWatchlists(1001L)
        );
    }

    private Account activeAccount(Long id) {
        Account account = new Account();
        account.setAccountId(id);
        account.setAccountReference("ACC-" + id);
        account.setCashBalance(new BigDecimal("100000.00"));
        account.setBuyingPower(new BigDecimal("100000.00"));
        account.setStatus(AccountStatus.ACTIVE);
        account.setVersion(1);
        account.setLastUpdated(LocalDateTime.of(2026, 9, 29, 9, 0));
        return account;
    }

    private Watchlist watchlist(
            Long id,
            Long accountId,
            String name,
            int entryCount
    ) {
        Watchlist watchlist = new Watchlist();
        watchlist.setId(id);
        watchlist.setAccountId(accountId);
        watchlist.setName(name);
        watchlist.setEntryCount(entryCount);
        watchlist.setCreatedOn(OffsetDateTime.of(2026, 9, 29, 10, 0, 0, 0, ZoneOffset.UTC));
        watchlist.setUpdatedOn(OffsetDateTime.of(2026, 9, 29, 10, 30, 0, 0, ZoneOffset.UTC));
        return watchlist;
    }

    private WatchlistEntry watchlistEntry(
            Long watchlistId,
            String symbol,
            LocalDateTime addedOn
    ) {
        WatchlistEntry entry = new WatchlistEntry();
        entry.setWatchlistId(watchlistId);
        entry.setSymbol(symbol);
        entry.setAddedOn(addedOn.atOffset(ZoneOffset.UTC));
        return entry;
    }


    private Instrument instrument(String symbol) {
        Instrument instrument = new Instrument();
        instrument.setSymbol(symbol);
        instrument.setCurrency("USD");
        instrument.setTradable(true);
        return instrument;
    }
}

