package com.tradingsystem.mapper;

import com.tradingsystem.enums.AlertDirection;
import com.tradingsystem.enums.AlertStatus;
import com.tradingsystem.model.PriceAlert;
import com.tradingsystem.model.Watchlist;
import com.tradingsystem.model.WatchlistEntry;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.boot.test.autoconfigure.MybatisTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.jdbc.Sql;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@MybatisTest
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:watchlistmappertests;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "mybatis.mapper-locations=classpath*:mapper/*.xml",
        "mybatis.type-handlers-package=com.tradingsystem.mapper.typehandler"
})
@Sql(
        scripts = {
                "/schema-watchlist-mybatis-test.sql",
                "/data-watchlist-mybatis-test.sql"
        },
        executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD
)
class WatchlistMyBatisIntegrationTest {

    @Autowired
    private WatchlistDataMapper watchlistDataMapper;

    @Autowired
    private PriceAlertDataMapper priceAlertDataMapper;


    @Test
    void watchlist_insert_and_entry_round_trip() {
        Watchlist watchlist = new Watchlist();
        watchlist.setAccountId(1001L);
        watchlist.setName("Banking");

        int insertedWatchlists = watchlistDataMapper.insertWatchlist(watchlist);
        assertEquals(1, insertedWatchlists);
        assertNotNull(watchlist.getId());

        WatchlistEntry entry = new WatchlistEntry();
        entry.setWatchlistId(watchlist.getId());
        entry.setSymbol("MSFT");

        int insertedEntries = watchlistDataMapper.insertWatchlistEntry(entry);
        assertEquals(1, insertedEntries);

        List<Watchlist> watchlists = watchlistDataMapper.findWatchlistsByAccountId(1001L);
        assertEquals(2, watchlists.size());
        assertTrue(watchlists.stream().anyMatch(item -> "Banking".equals(item.getName()) && item.getEntryCount() == 1));

        List<WatchlistEntry> entries = watchlistDataMapper.findEntriesByWatchlistId(watchlist.getId());
        assertEquals(1, entries.size());
        assertEquals("MSFT", entries.getFirst().getSymbol());
    }

    @Test
    void duplicate_watchlist_name_and_duplicate_symbol_are_rejected() {
        Watchlist duplicateWatchlist = new Watchlist();
        duplicateWatchlist.setAccountId(1001L);
        duplicateWatchlist.setName("Tech");

        assertThrows(
                DataIntegrityViolationException.class,
                () -> watchlistDataMapper.insertWatchlist(duplicateWatchlist)
        );

        WatchlistEntry duplicateEntry = new WatchlistEntry();
        duplicateEntry.setWatchlistId(10L);
        duplicateEntry.setSymbol("AAPL");

        assertThrows(
                DataIntegrityViolationException.class,
                () -> watchlistDataMapper.insertWatchlistEntry(duplicateEntry)
        );
    }

    @Test
    void alert_insert_creates_independent_alert_for_any_symbol() {
        // Alerts are now independent of watchlists and can be created for any supported symbol
        PriceAlert alert1 = new PriceAlert();
        alert1.setAccountId(1001L);
        alert1.setSymbol("AAPL");
        alert1.setDirection(AlertDirection.BELOW);
        alert1.setThreshold(new BigDecimal("220.00"));
        alert1.setStatus(AlertStatus.ACTIVE);

        int inserted = priceAlertDataMapper.insertPriceAlert(alert1);
        assertEquals(1, inserted);
        assertNotNull(alert1.getId());

        // Alert 2 for a different symbol
        PriceAlert alert2 = new PriceAlert();
        alert2.setAccountId(1001L);
        alert2.setSymbol("MSFT");
        alert2.setDirection(AlertDirection.ABOVE);
        alert2.setThreshold(new BigDecimal("400.00"));
        alert2.setStatus(AlertStatus.ACTIVE);

        int inserted2 = priceAlertDataMapper.insertPriceAlert(alert2);
        assertEquals(1, inserted2);
        assertNotNull(alert2.getId());

        assertEquals(2, priceAlertDataMapper.countActiveAlertsByAccountId(1001L));

        // Alert for different account
        PriceAlert alert3 = new PriceAlert();
        alert3.setAccountId(2002L);
        alert3.setSymbol("AAPL");
        alert3.setDirection(AlertDirection.ABOVE);
        alert3.setThreshold(new BigDecimal("250.00"));
        alert3.setStatus(AlertStatus.ACTIVE);

        int inserted3 = priceAlertDataMapper.insertPriceAlert(alert3);
        assertEquals(1, inserted3);

        // Only account 1001's alerts counted for their account
        assertEquals(2, priceAlertDataMapper.countActiveAlertsByAccountId(1001L));
        assertEquals(1, priceAlertDataMapper.countActiveAlertsByAccountId(2002L));
    }

    @Test
    void alerts_can_be_queried_by_symbol_for_market_data_hot_path() {
        // Create multiple alerts for the same symbol by different accounts
        PriceAlert alert1 = new PriceAlert();
        alert1.setAccountId(1001L);
        alert1.setSymbol("AAPL");
        alert1.setDirection(AlertDirection.BELOW);
        alert1.setThreshold(new BigDecimal("220.00"));
        alert1.setStatus(AlertStatus.ACTIVE);
        priceAlertDataMapper.insertPriceAlert(alert1);

        PriceAlert alert2 = new PriceAlert();
        alert2.setAccountId(2002L);
        alert2.setSymbol("AAPL");
        alert2.setDirection(AlertDirection.ABOVE);
        alert2.setThreshold(new BigDecimal("250.00"));
        alert2.setStatus(AlertStatus.ACTIVE);
        priceAlertDataMapper.insertPriceAlert(alert2);

        // Find all ACTIVE alerts by symbol
        List<PriceAlert> activeAaplAlerts = priceAlertDataMapper.findActiveAlertsBySymbol("AAPL");
        assertEquals(2, activeAaplAlerts.size());

        // No ACTIVE alerts for MSFT
        List<PriceAlert> activeMsftAlerts = priceAlertDataMapper.findActiveAlertsBySymbol("MSFT");
        assertEquals(0, activeMsftAlerts.size());
    }

    @Test
    void alert_status_can_be_updated_when_triggered() {
        PriceAlert alert = new PriceAlert();
        alert.setAccountId(1001L);
        alert.setSymbol("AAPL");
        alert.setDirection(AlertDirection.BELOW);
        alert.setThreshold(new BigDecimal("210.00"));
        alert.setStatus(AlertStatus.ACTIVE);

        priceAlertDataMapper.insertPriceAlert(alert);
        assertNotNull(alert.getId());
        assertEquals(AlertStatus.ACTIVE, alert.getStatus());

        // Mark as triggered
        OffsetDateTime now = OffsetDateTime.now();
        priceAlertDataMapper.updateAlertStatus(1001L, alert.getId(), "TRIGGERED", now);

        PriceAlert updated = priceAlertDataMapper.findAlertById(alert.getId());
        assertEquals(AlertStatus.TRIGGERED, updated.getStatus());
        assertNotNull(updated.getTriggeredOn());
    }
}

