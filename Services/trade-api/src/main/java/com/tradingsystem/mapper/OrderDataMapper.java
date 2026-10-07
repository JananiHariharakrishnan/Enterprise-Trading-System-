package com.tradingsystem.mapper;

import com.tradingsystem.dto.response.OrderHistoryEntry;
import com.tradingsystem.model.Account;
import com.tradingsystem.model.Instrument;
import com.tradingsystem.model.Order;
import com.tradingsystem.model.Position;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Mapper
public interface OrderDataMapper {

    Account findAccountById(@Param("accountId") Long accountId);

    Instrument findInstrumentBySymbol(@Param("symbol") String symbol);

    Position findPosition(@Param("accountId") Long accountId, @Param("symbol") String symbol);

    boolean existsByIdempotencyKey(@Param("idempotencyKey") String idempotencyKey);

    int insertOrder(Order order);

    int updateAccountBalancesWithVersion(
            @Param("accountId") Long accountId,
            @Param("cashBalance") BigDecimal cashBalance,
            @Param("buyingPower") BigDecimal buyingPower,
            @Param("expectedVersion") int expectedVersion
    );

    int insertPosition(
            @Param("accountId") Long accountId,
            @Param("symbol") String symbol,
            @Param("quantity") int quantity,
            @Param("averageCost") BigDecimal averageCost
    );

    int updatePosition(
            @Param("accountId") Long accountId,
            @Param("symbol") String symbol,
            @Param("quantity") int quantity,
            @Param("averageCost") BigDecimal averageCost
    );

    int updatePositionWithVersion(
            @Param("accountId") Long accountId,
            @Param("symbol") String symbol,
            @Param("quantity") int quantity,
            @Param("averageCost") BigDecimal averageCost,
            @Param("expectedVersion") int expectedVersion
    );

    int cancelOrderIfStatus(
            @Param("orderId") UUID orderId,
            @Param("expectedStatus") String expectedStatus,
            @Param("newStatus") String newStatus
    );

    List<OrderHistoryEntry> findOrderHistory(
            @Param("accountId") Long accountId,
            @Param("status") String status,
            @Param("from") OffsetDateTime from,
            @Param("to") OffsetDateTime to
    );

    // EDGE CASE: For order cancellation validation
    Order findOrderById(@Param("orderId") UUID orderId);

    // New methods for execution processing:
    int updateOrderStatusIfStatus(
            @Param("orderId") UUID orderId,
            @Param("expectedStatus") String expectedStatus,
            @Param("newStatus") String newStatus
    );

    int insertExecution(
            @Param("orderId") UUID orderId,
            @Param("quantity") int quantity,
            @Param("price") java.math.BigDecimal price
    );

    int insertExecutionWithEventId(
            @Param("orderId") UUID orderId,
            @Param("quantity") int quantity,
            @Param("price") java.math.BigDecimal price,
            @Param("eventId") UUID eventId
    );

    boolean existsExecutionByEventId(@Param("eventId") UUID eventId);

    List<String> findDistinctOrderedSymbolsByAccountId(@Param("accountId") Long accountId);
}