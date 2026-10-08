package com.tradingsystem.service.impl;

import com.tradingsystem.dto.response.BalanceResponse;
import com.tradingsystem.service.Impl.PortfolioServiceImpl;
import com.tradingsystem.dto.response.LatestMarketPrice;
import com.tradingsystem.dto.response.MoversResponse;
import com.tradingsystem.dto.response.PnlResponse;
import com.tradingsystem.dto.response.PricedPositionResponse;
import com.tradingsystem.dto.response.PortfolioSummaryResponse;
import com.tradingsystem.exception.AccountAccessDeniedException;
import com.tradingsystem.exception.InvalidPortfolioInputException;
import com.tradingsystem.exception.PricingUnavailableException;
import com.tradingsystem.exception.TradingAuthenticationException;
import com.tradingsystem.mapper.PortfolioDataMapper;
import com.tradingsystem.model.FillRecord;
import com.tradingsystem.model.Holding;
import com.tradingsystem.security.CurrentUser;
import com.tradingsystem.service.AccountService;
import com.tradingsystem.service.MarketDataService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PortfolioServiceImplTest {

    private static final Long ACCOUNT = 1L;

    @Mock
    private PortfolioDataMapper portfolioDataMapper;
    @Mock
    private AccountService accountService;
    @Mock
    private MarketDataService marketDataService;
    @Mock
    private CurrentUser currentUser;

    private PortfolioServiceImpl service;

    @BeforeEach
    void setUp() {
        service = newService("USD");
        lenient().when(currentUser.getAccountId()).thenReturn(ACCOUNT);
        lenient().when(portfolioDataMapper.findHoldings(anyLong())).thenReturn(List.of());
        lenient().when(portfolioDataMapper.findFilledExecutions(anyLong())).thenReturn(List.of());
        lenient().when(marketDataService.getLatestPrice(any())).thenReturn(null);
        lenient().when(marketDataService.getAllCachedPrices()).thenReturn(Map.of());
        lenient().when(accountService.getBalance(ACCOUNT)).thenReturn(balance("1000"));
    }

    @Test
    void getSummary_pricedUsdAndInrHoldings_includesRealisedAndPartial() {
        Holding aapl = holding("AAPL", 10, "10", "USD");
        Holding reliance = holding("RELIANCE", 96, "1", "INR");
        Holding missing = holding("GONE", 5, "4", "USD");
        when(portfolioDataMapper.findHoldings(ACCOUNT)).thenReturn(List.of(aapl, reliance, missing));
        when(marketDataService.getLatestPrice("AAPL")).thenReturn(quote("12", "1", "11", false, Instant.now().toString()));
        when(marketDataService.getLatestPrice("RELIANCE")).thenReturn(quote("2", null, null, false, Instant.now().toString()));
        when(portfolioDataMapper.findFilledExecutions(ACCOUNT)).thenReturn(List.of(
                fill("AAPL", "BUY", 10, "10", "USD", LocalDateTime.of(2026, 1, 1, 10, 0)),
                fill("AAPL", "SELL", 4, "15", "USD", LocalDateTime.of(2026, 1, 2, 10, 0))
        ));

        PortfolioSummaryResponse summary = service.getSummary(ACCOUNT);

        assertEquals(ACCOUNT, summary.accountId());
        assertEquals("USD", summary.baseCurrency());
        assertEquals(0, new BigDecimal("1000.00").compareTo(summary.cashBalance()));
        assertEquals(3, summary.positionCount());
        assertTrue(summary.partial());
        assertEquals(0, new BigDecimal("20.00").compareTo(summary.realisedPnl()));
        assertTrue(summary.marketValue().compareTo(BigDecimal.ZERO) > 0);
        assertTrue(summary.totalValue().compareTo(summary.cashBalance()) > 0);
    }

    @Test
    void getSummary_nullCashAndEmptyHoldings_returnsZeros() {
        when(accountService.getBalance(ACCOUNT)).thenReturn(balance(null));

        PortfolioSummaryResponse summary = service.getSummary(ACCOUNT);

        assertEquals(0, BigDecimal.ZERO.setScale(2).compareTo(summary.cashBalance()));
        assertEquals(0, summary.positionCount());
        assertFalse(summary.partial());
        assertEquals(0, BigDecimal.ZERO.setScale(2).compareTo(summary.realisedPnl()));
    }

    @Test
    void getSummary_convertsUsdHoldingIntoInrBase() {
        PortfolioServiceImpl inrService = newService("INR");
        when(portfolioDataMapper.findHoldings(ACCOUNT)).thenReturn(List.of(holding("AAPL", 2, "10", "USD")));
        when(marketDataService.getLatestPrice("AAPL")).thenReturn(quote("15", "1", "14", false, Instant.now().toString()));

        PortfolioSummaryResponse summary = inrService.getSummary(ACCOUNT);

        assertEquals("INR", summary.baseCurrency());
        assertEquals(0, new BigDecimal("2880.00").compareTo(summary.marketValue()));
    }

    @Test
    void getSummary_unsupportedCurrency_throws() {
        when(portfolioDataMapper.findHoldings(ACCOUNT)).thenReturn(List.of(holding("EURX", 1, "10", "EUR")));
        when(marketDataService.getLatestPrice("EURX")).thenReturn(quote("12", null, null, false, Instant.now().toString()));

        assertThrows(IllegalArgumentException.class, () -> service.getSummary(ACCOUNT));
    }

    @Test
    void getSummary_noHoldingPriced_throwsPricingUnavailable() {
        when(portfolioDataMapper.findHoldings(ACCOUNT)).thenReturn(List.of(holding("AAPL", 1, "10", "USD")));
        LatestMarketPrice blank = new LatestMarketPrice();
        when(marketDataService.getLatestPrice("AAPL")).thenReturn(blank);

        assertThrows(PricingUnavailableException.class, () -> service.getSummary(ACCOUNT));
    }

    @Test
    void getPricedPositions_filtersSymbolAndKeepsUnpricedLine() {
        when(portfolioDataMapper.findHoldings(ACCOUNT)).thenReturn(List.of(
                holding("AAPL", 2, "10", "USD"),
                holding("MSFT", 3, "20", "USD"),
                holding("IBM", 1, "5", null)
        ));
        when(marketDataService.getLatestPrice("AAPL")).thenReturn(quote("15", "1", "14", true, "not-a-timestamp"));
        when(marketDataService.getLatestPrice("IBM")).thenReturn(quote("8", null, null, false, Instant.now().toString()));

        List<PricedPositionResponse> filtered = service.getPricedPositions(ACCOUNT, "  aapl ");
        assertEquals(1, filtered.size());
        assertEquals("AAPL", filtered.get(0).symbol());
        assertNull(filtered.get(0).priceAsOf());
        assertTrue(filtered.get(0).stale());
        assertEquals(0, new BigDecimal("10.00").compareTo(filtered.get(0).unrealisedPnl()));

        List<PricedPositionResponse> all = service.getPricedPositions(ACCOUNT, "   ");
        assertEquals(3, all.size());
        PricedPositionResponse msft = all.stream().filter(p -> "MSFT".equals(p.symbol())).findFirst().orElseThrow();
        assertNull(msft.lastPrice());
        assertNull(msft.unrealisedPnlPercent());
        assertTrue(msft.stale());

        assertEquals(3, service.getPricedPositions(ACCOUNT, null).size());
    }

    @Test
    void getPnl_rejectsInvertedRangeBeforeLoadingBalance() {
        assertThrows(InvalidPortfolioInputException.class,
                () -> service.getPnl(ACCOUNT, LocalDate.of(2026, 2, 2), LocalDate.of(2026, 1, 1), false));
        verify(accountService, never()).getBalance(anyLong());
    }

    @Test
    void getPnl_appliesDateWindowAndSymbolBreakdown() {
        when(portfolioDataMapper.findHoldings(ACCOUNT)).thenReturn(List.of(
                holding("AAPL", 2, "10", "USD"),
                holding("MSFT", 1, "30", "INR")
        ));
        when(marketDataService.getLatestPrice("AAPL")).thenReturn(quote("12", null, null, false, Instant.now().toString()));
        when(marketDataService.getLatestPrice("MSFT")).thenReturn(quote("126", null, null, false, Instant.now().toString()));
        when(portfolioDataMapper.findFilledExecutions(ACCOUNT)).thenReturn(List.of(
                fill("AAPL", "BUY", 10, "10", "USD", LocalDateTime.of(2026, 1, 1, 9, 0)),
                fill("AAPL", "SELL", 4, "15", "USD", LocalDateTime.of(2026, 1, 10, 9, 0)),
                fill("IBM", "BUY", 2, "5", "USD", LocalDateTime.of(2026, 3, 1, 9, 0)),
                fill("IBM", "SELL", 2, "8", "USD", LocalDateTime.of(2026, 3, 15, 9, 0)),
                fill("OLD", "BUY", 1, "4", "USD", LocalDateTime.of(2025, 12, 1, 9, 0)),
                fill("OLD", "SELL", 1, "9", "USD", LocalDateTime.of(2025, 12, 20, 9, 0))
        ));

        PnlResponse ranged = service.getPnl(ACCOUNT, LocalDate.of(2026, 1, 10), LocalDate.of(2026, 2, 1), true);
        assertEquals("2026-01-10", ranged.from());
        assertEquals("2026-02-01", ranged.to());
        assertEquals(0, new BigDecimal("20.00").compareTo(ranged.realisedPnl()));
        assertEquals(2, ranged.bySymbol().size());
        assertTrue(ranged.bySymbol().stream().anyMatch(s -> "AAPL".equals(s.symbol())));
        assertTrue(ranged.bySymbol().stream().anyMatch(s -> "MSFT".equals(s.symbol())));

        PnlResponse openEnded = service.getPnl(ACCOUNT, null, LocalDate.of(2026, 1, 31), false);
        assertNull(openEnded.from());
        assertNull(openEnded.bySymbol());
        assertEquals(0, new BigDecimal("25.00").compareTo(openEnded.realisedPnl()));

        PnlResponse fromOnly = service.getPnl(ACCOUNT, LocalDate.of(2026, 3, 1), null, true);
        assertNull(fromOnly.to());
        assertEquals(0, new BigDecimal("6.00").compareTo(fromOnly.realisedPnl()));
        assertTrue(fromOnly.bySymbol().stream().anyMatch(s -> "IBM".equals(s.symbol()) && "AAPL".equals(s.symbol()) == false));
    }

    @Test
    void getMovers_totalBasisRanksGainersAndLosersAndSkipsFlat() {
        when(portfolioDataMapper.findHoldings(ACCOUNT)).thenReturn(List.of(
                holding("AAPL", 10, "10", "USD"),
                holding("IBM", 10, "10", "USD"),
                holding("MSFT", 10, "10", "USD"),
                holding("FLAT", 10, "10", "USD"),
                holding("GONE", 4, "4", "USD")
        ));
        when(marketDataService.getLatestPrice("AAPL")).thenReturn(quote("15", "2", "13", false, Instant.now().toString()));
        when(marketDataService.getLatestPrice("IBM")).thenReturn(quote("11", "1", "10", false, Instant.now().toString()));
        when(marketDataService.getLatestPrice("MSFT")).thenReturn(quote("8", "-1", "9", false, Instant.now().toString()));
        when(marketDataService.getLatestPrice("FLAT")).thenReturn(quote("10", "0", "10", false, Instant.now().toString()));

        MoversResponse movers = service.getMovers(ACCOUNT, 1, " Total ");

        assertEquals("total", movers.basis());
        assertTrue(movers.partial());
        assertEquals(1, movers.gainers().size());
        assertEquals("AAPL", movers.gainers().get(0).symbol());
        assertEquals(1, movers.losers().size());
        assertEquals("MSFT", movers.losers().get(0).symbol());
    }

    @Test
    void getMovers_dayBasisSkipsMissingChangeAndUsesPreviousClose() {
        when(portfolioDataMapper.findHoldings(ACCOUNT)).thenReturn(List.of(
                holding("AAPL", 2, "10", "USD"),
                holding("MSFT", 2, "10", "USD"),
                holding("NFLX", 2, "10", "INR")
        ));
        when(marketDataService.getLatestPrice("AAPL")).thenReturn(quote("12", "3", "10", false, Instant.now().toString()));
        when(marketDataService.getLatestPrice("MSFT")).thenReturn(quote("12", null, "10", false, Instant.now().toString()));
        when(marketDataService.getLatestPrice("NFLX")).thenReturn(quote("20", "-96", null, true, Instant.now().toString()));

        MoversResponse day = service.getMovers(ACCOUNT, 0, "DAY");
        assertEquals("day", day.basis());
        assertEquals(1, day.gainers().size());
        assertEquals("AAPL", day.gainers().get(0).symbol());
        assertEquals(1, day.losers().size());
        assertNull(day.losers().get(0).pnlPercent());

        MoversResponse capped = service.getMovers(ACCOUNT, 80, null);
        assertEquals("total", capped.basis());
        assertFalse(capped.gainers().isEmpty());
    }

    @Test
    void getMovers_rejectsUnknownBasis() {
        assertThrows(InvalidPortfolioInputException.class, () -> service.getMovers(ACCOUNT, 5, "week"));
    }

    @Test
    void getHealth_reportsOkDegradedAndDown() {
        when(portfolioDataMapper.ping()).thenReturn(1);
        when(marketDataService.getAllCachedPrices()).thenReturn(Map.of(
                "AAPL", quote("10", null, null, false, Instant.now().toString())
        ));
        assertEquals("ok", service.getHealth().status());
        assertEquals("ok", service.getHealth().dependencies().get(0).status());
        assertEquals("ok", service.getHealth().dependencies().get(1).status());

        when(marketDataService.getAllCachedPrices()).thenReturn(Map.of(
                "AAPL", quote("10", null, null, true, Instant.now().minusSeconds(10_000).toString())
        ));
        assertEquals("degraded", service.getHealth().status());
        assertEquals("degraded", service.getHealth().dependencies().get(1).status());

        Map<String, LatestMarketPrice> nullQuote = new HashMap<>();
        nullQuote.put("X", null);
        when(marketDataService.getAllCachedPrices()).thenReturn(nullQuote);
        assertEquals("degraded", service.getHealth().dependencies().get(1).status());

        when(marketDataService.getAllCachedPrices()).thenReturn(Map.of());
        when(portfolioDataMapper.ping()).thenReturn(0);
        var down = service.getHealth();
        assertEquals("down", down.dependencies().get(0).status());
        assertEquals("down", down.dependencies().get(1).status());
        assertEquals("degraded", down.status());

        doThrow(new RuntimeException("db")).when(portfolioDataMapper).ping();
        assertEquals("down", service.getHealth().dependencies().get(0).status());
    }

    @Test
    void authorise_rejectsMissingTokenWrongAccountAndNullAccount() {
        doThrow(new IllegalStateException("no token")).when(currentUser).getAccountId();
        assertThrows(TradingAuthenticationException.class, () -> service.getSummary(ACCOUNT));

        doReturn(null).when(currentUser).getAccountId();
        assertThrows(TradingAuthenticationException.class, () -> service.getPnl(ACCOUNT, null, null, false));

        doReturn(99L).when(currentUser).getAccountId();
        assertThrows(AccountAccessDeniedException.class, () -> service.getMovers(ACCOUNT, 5, "total"));
        assertThrows(AccountAccessDeniedException.class, () -> service.getSummary(null));
    }

    private PortfolioServiceImpl newService(String baseCurrency) {
        return new PortfolioServiceImpl(
                portfolioDataMapper,
                accountService,
                marketDataService,
                currentUser,
                baseCurrency,
                new BigDecimal("96"),
                300L);
    }

    private static BalanceResponse balance(String cash) {
        BalanceResponse response = new BalanceResponse();
        response.setCashBalance(cash == null ? null : new BigDecimal(cash));
        return response;
    }

    private static Holding holding(String symbol, int quantity, String averageCost, String currency) {
        Holding holding = new Holding();
        holding.setSymbol(symbol);
        holding.setQuantity(quantity);
        holding.setAverageCost(new BigDecimal(averageCost));
        holding.setCurrency(currency);
        return holding;
    }

    private static LatestMarketPrice quote(String price, String change, String previousClose, boolean stale, String asOf) {
        LatestMarketPrice quote = new LatestMarketPrice();
        quote.setPrice(price == null ? null : new BigDecimal(price));
        quote.setChange(change == null ? null : new BigDecimal(change));
        quote.setPreviousClose(previousClose == null ? null : new BigDecimal(previousClose));
        quote.setStale(stale);
        quote.setQuoteAsOf(asOf);
        quote.setCurrency("USD");
        quote.setMarketState("OPEN");
        return quote;
    }

    private static FillRecord fill(String symbol, String side, int quantity, String price, String currency, LocalDateTime when) {
        return new FillRecord(symbol, side, quantity, new BigDecimal(price), when, currency);
    }
}
