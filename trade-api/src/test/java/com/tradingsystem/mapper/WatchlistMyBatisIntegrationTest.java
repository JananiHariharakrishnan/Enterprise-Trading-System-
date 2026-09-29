package com.tradingsystem.mapper;

import com.tradingsystem.enums.AlertDirection;
import com.tradingsystem.enums.AlertStatus;
import com.tradingsystem.enums.NotificationStatus;
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
import java.time.LocalDateTime;
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
    void alert_insert_respects_watchlist_membership_and_account_scope() {
        PriceAlert validAlert = new PriceAlert();
        validAlert.setAccountId(1001L);
        validAlert.setWatchlistId(10L);
        validAlert.setSymbol("AAPL");
        validAlert.setDirection(AlertDirection.BELOW_OR_EQUAL);
        validAlert.setThreshold(new BigDecimal("220.00"));
        validAlert.setStatus(AlertStatus.ACTIVE);
        validAlert.setNotificationStatus(NotificationStatus.PENDING);

        int inserted = priceAlertDataMapper.insertPriceAlert(validAlert);
        assertEquals(1, inserted);
        assertNotNull(validAlert.getId());

        assertEquals(2, priceAlertDataMapper.countActiveAlertsByAccountId(1001L));

        PriceAlert wrongAccount = new PriceAlert();
        wrongAccount.setAccountId(2002L);
        wrongAccount.setWatchlistId(10L);
        wrongAccount.setSymbol("AAPL");
        wrongAccount.setDirection(AlertDirection.ABOVE_OR_EQUAL);
        wrongAccount.setThreshold(new BigDecimal("250.00"));
        wrongAccount.setStatus(AlertStatus.ACTIVE);
        wrongAccount.setNotificationStatus(NotificationStatus.PENDING);

        assertThrows(
                DataIntegrityViolationException.class,
                () -> priceAlertDataMapper.insertPriceAlert(wrongAccount)
        );

        PriceAlert missingEntry = new PriceAlert();
        missingEntry.setAccountId(1001L);
        missingEntry.setWatchlistId(10L);
        missingEntry.setSymbol("MSFT");
        missingEntry.setDirection(AlertDirection.ABOVE_OR_EQUAL);
        missingEntry.setThreshold(new BigDecimal("250.00"));
        missingEntry.setStatus(AlertStatus.ACTIVE);
        missingEntry.setNotificationStatus(NotificationStatus.PENDING);

        assertThrows(
                DataIntegrityViolationException.class,
                () -> priceAlertDataMapper.insertPriceAlert(missingEntry)
        );
    }

    @Test
    void deleting_watchlist_cascades_entries_and_alerts() {
        PriceAlert alert = new PriceAlert();
        alert.setAccountId(1001L);
        alert.setWatchlistId(10L);
        alert.setSymbol("AAPL");
        alert.setDirection(AlertDirection.BELOW_OR_EQUAL);
        alert.setThreshold(new BigDecimal("210.00"));
        alert.setStatus(AlertStatus.ACTIVE);
        alert.setNotificationStatus(NotificationStatus.PENDING);

        priceAlertDataMapper.insertPriceAlert(alert);
        assertNotNull(priceAlertDataMapper.findAlertById(alert.getId()));

        int deleted = watchlistDataMapper.deleteWatchlist(1001L, 10L);
        assertEquals(1, deleted);

        assertTrue(watchlistDataMapper.findEntriesByWatchlistId(10L).isEmpty());
        assertNull(priceAlertDataMapper.findAlertById(alert.getId()));
        assertNull(watchlistDataMapper.findWatchlistById(10L));
    }
}

