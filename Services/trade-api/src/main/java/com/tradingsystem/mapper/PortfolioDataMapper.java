package com.tradingsystem.mapper;

import com.tradingsystem.model.FillRecord;
import com.tradingsystem.model.Holding;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * READ-ONLY access for the portfolio module. SELECT statements only:
 * nothing here may write to accounts, orders, executions or positions.
 */
@Mapper
public interface PortfolioDataMapper {

    /** Open positions (qty > 0) with the instrument's own currency. */
    List<Holding> findHoldings(@Param("accountId") Long accountId);

    /** Every execution of a FILLED order for the account, oldest first. */
    List<FillRecord> findFilledExecutions(@Param("accountId") Long accountId);

    /** Liveness probe for /health. */
    int ping();
}
