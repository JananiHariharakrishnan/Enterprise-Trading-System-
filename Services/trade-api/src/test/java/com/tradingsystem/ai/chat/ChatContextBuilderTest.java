package com.tradingsystem.ai.chat;

import com.tradingsystem.ai.signal.TradeSignalStore;
import com.tradingsystem.dto.response.LatestMarketPrice;
import com.tradingsystem.dto.response.OrderHistoryEntry;
import com.tradingsystem.dto.response.WatchlistSummaryResponse;
import com.tradingsystem.enums.OrderSide;
import com.tradingsystem.enums.OrderStatus;
import com.tradingsystem.mapper.AccountDataMapper;
import com.tradingsystem.mapper.OrderDataMapper;
import com.tradingsystem.model.Account;
import com.tradingsystem.model.Position;
import com.tradingsystem.service.MarketDataService;
import com.tradingsystem.service.WatchlistService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatContextBuilderTest {

    @Mock
    private TradeSignalStore signalStore;
    @Mock
    private MarketDataService marketDataService;
    @Mock
    private WatchlistService watchlistService;
    @Mock
    private AccountDataMapper accountDataMapper;
    @Mock
    private OrderDataMapper orderDataMapper;

    private ChatContextBuilder builder;

    @BeforeEach
    void setUp() {
        builder = new ChatContextBuilder(
                signalStore,
                marketDataService,
                watchlistService,
                accountDataMapper,
                orderDataMapper
        );
    }

    @Test
    void build_includesPortfolioHoldingsOrdersPricesAndWatchlists() {
        Account account = new Account();
        account.setAccountId(7L);
        account.setCashBalance(new BigDecimal("1500.50"));
        account.setBuyingPower(new BigDecimal("900.25"));

        Position held = new Position();
        held.setSymbol("AAPL");
        held.setQuantity(4);
        held.setAverageCost(new BigDecimal("180.00"));

        Position flat = new Position();
        flat.setSymbol("FLAT");
        flat.setQuantity(0);
        flat.setAverageCost(new BigDecimal("1.00"));

        OrderHistoryEntry priced = order("BUY", "AAPL", 2, new BigDecimal("181.25"), OrderStatus.NEW);
        OrderHistoryEntry market = order("SELL", "MSFT", 1, null, OrderStatus.FILLED);
        List<OrderHistoryEntry> orders = List.of(priced, market, priced, priced, priced, priced);

        LatestMarketPrice usd = new LatestMarketPrice();
        usd.setPrice(new BigDecimal("190.5000"));
        usd.setCurrency("USD");

        LatestMarketPrice noCurrency = new LatestMarketPrice();
        noCurrency.setPrice(new BigDecimal("20"));
        noCurrency.setCurrency(null);

        LatestMarketPrice missingPrice = new LatestMarketPrice();
        missingPrice.setPrice(null);
        missingPrice.setCurrency("USD");

        Map<String, LatestMarketPrice> prices = new HashMap<>();
        prices.put("AAPL", usd);
        prices.put("TCS.NS", noCurrency);
        prices.put("SKIP", missingPrice);
        prices.put("NULL", null);

        WatchlistSummaryResponse watchlist = new WatchlistSummaryResponse();
        watchlist.setId(3L);
        watchlist.setName("Core");

        when(accountDataMapper.findAccountById(7L)).thenReturn(account);
        when(accountDataMapper.findPositionsByAccountId(7L)).thenReturn(List.of(held, flat));
        when(orderDataMapper.findOrderHistory(7L, null, null, null)).thenReturn(orders);
        when(signalStore.buildSummary()).thenReturn("AAPL BUY");
        when(marketDataService.getAllCachedPrices()).thenReturn(prices);
        when(watchlistService.listWatchlists(7L)).thenReturn(List.of(watchlist));

        String prompt = builder.build(7L, "How is AAPL?");

        assertTrue(prompt.contains("Account ID: 7 | Cash Balance: $1500.50 | Buying Power: $900.25"));
        assertTrue(prompt.contains("AAPL: 4 shares @ avg cost $180.00"));
        assertFalse(prompt.contains("FLAT"));
        assertTrue(prompt.contains("BUY AAPL 2 shares @ $181.25 (status: NEW)"));
        assertTrue(prompt.contains("SELL MSFT 1 shares @ MARKET (status: FILLED)"));
        assertTrue(prompt.contains("AAPL BUY"));
        assertTrue(prompt.contains("AAPL: 190.5000 USD"));
        assertTrue(prompt.contains("TCS.NS: 20.0000"));
        assertTrue(prompt.contains("Watchlist: Core (id=3)"));
        assertTrue(prompt.contains("User question: How is AAPL?"));
        assertFalse(prompt.contains("SKIP"));
    }

    @Test
    void build_reportsEmptySectionsWhenAccountAndCachesAreMissing() {
        when(accountDataMapper.findAccountById(8L)).thenReturn(null);
        when(accountDataMapper.findPositionsByAccountId(8L)).thenReturn(List.of());
        when(orderDataMapper.findOrderHistory(8L, null, null, null)).thenReturn(null);
        when(signalStore.buildSummary()).thenReturn("none");
        when(marketDataService.getAllCachedPrices()).thenReturn(Map.of());
        when(watchlistService.listWatchlists(8L)).thenReturn(List.of());

        String prompt = builder.build(8L, "status");

        assertTrue(prompt.contains("Current Holdings: No open positions."));
        assertTrue(prompt.contains("Recent Orders: No order history yet."));
        assertTrue(prompt.contains("No market prices cached yet."));
        assertTrue(prompt.contains("No watchlists found."));
        assertFalse(prompt.contains("Account ID:"));
    }

    @Test
    void build_treatsPriceMapThatFiltersToNothingAsUncached() {
        LatestMarketPrice blank = new LatestMarketPrice();
        blank.setPrice(null);
        when(accountDataMapper.findAccountById(8L)).thenReturn(null);
        when(accountDataMapper.findPositionsByAccountId(8L)).thenReturn(null);
        when(orderDataMapper.findOrderHistory(8L, null, null, null)).thenReturn(List.of());
        when(signalStore.buildSummary()).thenReturn("signals");
        when(marketDataService.getAllCachedPrices()).thenReturn(Map.of("X", blank));
        when(watchlistService.listWatchlists(8L)).thenReturn(List.of());

        String prompt = builder.build(8L, "prices");

        assertTrue(prompt.contains("No market prices cached yet."));
        assertTrue(prompt.contains("Current Holdings: No open positions."));
    }

    @Test
    void build_withoutAccountId_skipsPortfolioLookup() {
        when(signalStore.buildSummary()).thenReturn("signals");
        when(marketDataService.getAllCachedPrices()).thenReturn(Map.of());
        when(watchlistService.listWatchlists(null)).thenReturn(List.of());

        String prompt = builder.build(null, "hello");

        assertTrue(prompt.contains("No user account linked."));
        assertTrue(prompt.contains("User question: hello"));
    }

    @Test
    void build_swallowsCollaboratorFailures() {
        when(accountDataMapper.findAccountById(9L)).thenThrow(new RuntimeException("db down"));
        when(signalStore.buildSummary()).thenThrow(new RuntimeException("signal down"));
        when(marketDataService.getAllCachedPrices()).thenThrow(new RuntimeException("price down"));
        when(watchlistService.listWatchlists(9L)).thenThrow(new RuntimeException("watch down"));

        String prompt = builder.build(9L, "help");

        assertTrue(prompt.contains("Portfolio data unavailable."));
        assertTrue(prompt.contains("Signal data unavailable."));
        assertTrue(prompt.contains("Market price data unavailable."));
        assertTrue(prompt.contains("Watchlist data unavailable."));
    }

    private static OrderHistoryEntry order(String side, String symbol, int qty, BigDecimal price, OrderStatus status) {
        OrderHistoryEntry entry = new OrderHistoryEntry();
        entry.setSide(OrderSide.valueOf(side));
        entry.setSymbol(symbol);
        entry.setQuantity(qty);
        entry.setPrice(price);
        entry.setStatus(status);
        return entry;
    }
}
