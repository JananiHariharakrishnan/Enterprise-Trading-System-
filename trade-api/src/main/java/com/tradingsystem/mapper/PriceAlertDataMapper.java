package com.tradingsystem.mapper;

import com.tradingsystem.model.PriceAlert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface PriceAlertDataMapper {

    int insertPriceAlert(PriceAlert priceAlert);

    List<PriceAlert> findAlertsByAccountId(
            @Param("accountId") Long accountId,
            @Param("watchlistId") Long watchlistId,
            @Param("status") String status
    );

    PriceAlert findAlertById(@Param("alertId") Long alertId);

    PriceAlert findAlertByAccountIdAndId(
            @Param("accountId") Long accountId,
            @Param("alertId") Long alertId
    );

    int deleteAlert(
            @Param("accountId") Long accountId,
            @Param("alertId") Long alertId
    );

    int countActiveAlertsByAccountId(@Param("accountId") Long accountId);

    List<PriceAlert> findActiveAlertsBySymbol(@Param("symbol") String symbol);
}

