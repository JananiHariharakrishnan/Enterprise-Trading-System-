package com.tradingsystem.mapper;

import com.tradingsystem.dto.response.OrderHistoryEntry;
import com.tradingsystem.model.Account;
import com.tradingsystem.model.Instrument;
import com.tradingsystem.model.Order;
import com.tradingsystem.model.Position;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.OffsetDateTime;
import java.util.List;

@Mapper
public interface OrderDataMapper {

    Account findAccountById(@Param("accountId") Long accountId);

    Instrument findInstrumentBySymbol(@Param("symbol") String symbol);

    Position findPosition(@Param("accountId") Long accountId, @Param("symbol") String symbol);

    boolean existsByIdempotencyKey(@Param("idempotencyKey") String idempotencyKey);

    int insertOrder(Order order);

    List<OrderHistoryEntry> findOrderHistory(
            @Param("accountId") Long accountId,
            @Param("status") String status,
            @Param("from") OffsetDateTime from,
            @Param("to") OffsetDateTime to
    );
}

