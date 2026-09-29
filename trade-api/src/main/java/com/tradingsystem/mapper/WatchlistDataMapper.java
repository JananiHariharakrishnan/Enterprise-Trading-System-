package com.tradingsystem.mapper;

import com.tradingsystem.model.Instrument;
import com.tradingsystem.model.Watchlist;
import com.tradingsystem.model.WatchlistEntry;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface WatchlistDataMapper {

    int insertWatchlist(Watchlist watchlist);

    List<Watchlist> findWatchlistsByAccountId(@Param("accountId") Long accountId);

    Watchlist findWatchlistById(@Param("watchlistId") Long watchlistId);

    Watchlist findWatchlistByAccountIdAndId(
            @Param("accountId") Long accountId,
            @Param("watchlistId") Long watchlistId
    );

    int updateWatchlistName(
            @Param("accountId") Long accountId,
            @Param("watchlistId") Long watchlistId,
            @Param("name") String name
    );

    int deleteWatchlist(
            @Param("accountId") Long accountId,
            @Param("watchlistId") Long watchlistId
    );

    Instrument findInstrumentBySymbol(@Param("symbol") String symbol);

    int insertWatchlistEntry(WatchlistEntry watchlistEntry);

    int deleteWatchlistEntry(
            @Param("watchlistId") Long watchlistId,
            @Param("symbol") String symbol
    );

    List<WatchlistEntry> findEntriesByWatchlistId(@Param("watchlistId") Long watchlistId);

    boolean existsSymbolInWatchlist(
            @Param("watchlistId") Long watchlistId,
            @Param("symbol") String symbol
    );
}

