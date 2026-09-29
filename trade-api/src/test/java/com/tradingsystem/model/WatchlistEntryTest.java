package com.tradingsystem.model;

import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;

class WatchlistEntryTest {

    @Test
    void isRecentlyAdded_and_validate() {
        WatchlistEntry w = new WatchlistEntry();
        w.setWatchlistId(5L);
        w.setSymbol("ABC");
        w.setAddedOn(OffsetDateTime.now(ZoneOffset.UTC));

        assertTrue(w.isRecentlyAddedHours(1));
        w.validate();
        assertTrue(w.toString().contains("ABC"));
    }

    @Test
    void isRecentlyAdded_falseForOld() {
        WatchlistEntry w = new WatchlistEntry();
        w.setWatchlistId(5L);
        w.setSymbol("ABC");
        w.setAddedOn(OffsetDateTime.now(ZoneOffset.UTC).minusDays(2));

        assertFalse(w.isRecentlyAddedHours(1));
    }

    @Test
    void validate_throwsForMissing() {
        WatchlistEntry w = new WatchlistEntry();
        w.setWatchlistId(null);
        w.setSymbol("");
        assertThrows(IllegalArgumentException.class, w::validate);
    }
}
