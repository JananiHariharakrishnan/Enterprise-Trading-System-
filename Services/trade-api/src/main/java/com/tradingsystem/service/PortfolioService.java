package com.tradingsystem.service;

import com.tradingsystem.dto.response.HealthResponse;
import com.tradingsystem.dto.response.MoversResponse;
import com.tradingsystem.dto.response.PnlResponse;
import com.tradingsystem.dto.response.PortfolioSummaryResponse;
import com.tradingsystem.dto.response.PricedPositionResponse;

import java.time.LocalDate;
import java.util.List;

/**
 * Priced holdings and profit and loss. Read-only: nothing here writes to the
 * trading tables, and a pricing failure never touches the order path.
 *
 * Every method that takes an accountId compares it with the token's accountId
 * claim before reading anything (ACC-403 on mismatch).
 */
public interface PortfolioService {

    PortfolioSummaryResponse getSummary(Long accountId);

    /** @param symbol optional filter, null or blank for every holding */
    List<PricedPositionResponse> getPricedPositions(Long accountId, String symbol);

    /** from and to bound the realised figure only; unrealised is always as at now. */
    PnlResponse getPnl(Long accountId, LocalDate from, LocalDate to, boolean bySymbol);

    /** Top gainers and losers. basis is "total" (lifetime unrealised) or "day". */
    MoversResponse getMovers(Long accountId, int limit, String basis);

    /** Liveness plus dependency state. Needs no token. */
    HealthResponse getHealth();
}
