package com.tradingsystem.characterisation;

import com.tradingsystem.dto.response.OrderResponse;
import com.tradingsystem.enums.OrderSide;
import com.tradingsystem.enums.OrderStatus;
import com.tradingsystem.enums.OrderType;
import com.tradingsystem.exception.AccountNotActiveException;
import com.tradingsystem.exception.DuplicateOrderException;
import com.tradingsystem.exception.InstrumentNotFoundException;
import com.tradingsystem.exception.InsufficientFundsException;
import com.tradingsystem.exception.InsufficientHoldingsException;
import com.tradingsystem.security.AuthContextHolder;
import com.tradingsystem.service.OrderService;
import com.tradingsystem.service.command.PlaceOrderCommand;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.jdbc.Sql;

import java.math.BigDecimal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;


@SpringBootTest
@TestPropertySource(properties = {

        "spring.datasource.url=jdbc:h2:mem:characterisation;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",

        "mybatis.mapper-locations=classpath*:mapper/*.xml",
        "mybatis.type-handlers-package=com.tradingsystem.mapper.typehandler",

        "JWT_SECRET=test-secret-key-test-secret-key-test-secret-key"

})
@Sql(
        scripts = {
                "/schema-order-service-test.sql",
                "/data-order-service-test.sql"
        },
        executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD
)
class OrderPlacementCharacterisationTest {

    @Autowired
    private OrderService orderService;

    @Autowired
    private JdbcTemplate jdbcTemplate;


    @BeforeEach
    void setupAuthentication() {
        AuthContextHolder.setAccountId(1001L);
    }


    @AfterEach
    void clearAuthentication() {
        AuthContextHolder.clear();
    }


    // Mandated by the README:
    // Pin the affordable order response and persisted order, cash and position state.
    @Test
    void affordable_buy_pins_current_response_and_persistence() {

        PlaceOrderCommand command =
                buyCommand(
                        "char-affordable-001",
                        5,
                        "190.00"
                );


        OrderResponse response =
                orderService.placeOrder(command);


        assertNotNull(response.getOrderId());

        assertEquals(
                OrderStatus.NEW,
                response.getStatus()
        );

        assertEquals(
                "Order accepted",
                response.getMessage()
        );

        assertEquals(
                "AAPL",
                response.getSymbol()
        );

        assertEquals(
                OrderSide.BUY,
                response.getSide()
        );

        assertEquals(
                5,
                response.getQuantity()
        );

        assertEquals(
                new BigDecimal("190.00"),
                response.getPrice()
        );


        // Balance is not updated at placement time. Buying power is reserved for NEW BUY orders.
         assertEquals(
                new BigDecimal("100000.00"),
                cashBalance(1001L)
        );

        assertEquals(
                new BigDecimal("99050.00"),
                buyingPower(1001L)
        );

        // Position is also NOT created at placement time, only at execution
         assertEquals(
                15,
                positionQuantity(
                        1001L,
                        "AAPL"
                )
        );

        assertEquals(
                new BigDecimal("171.50"),
                positionAverageCost(
                        1001L,
                        "AAPL"
                )
        );


        Map<String, Object> orderRow =
                jdbcTemplate.queryForMap(
                        """
                        SELECT account_id,
                               symbol,
                               side,
                               order_type,
                               qty,
                               price,
                               status,
                               idempotency_key
                        FROM orders
                        WHERE idempotency_key = ?
                        """,
                        "char-affordable-001"
                );


        assertEquals(
                1001L,
                ((Number) orderRow.get("account_id")).longValue()
        );

        assertEquals(
                "AAPL",
                orderRow.get("symbol")
        );

        assertEquals(
                "BUY",
                orderRow.get("side")
        );

        assertEquals(
                "LIMIT",
                orderRow.get("order_type")
        );

        assertEquals(
                5,
                ((Number) orderRow.get("qty")).intValue()
        );

        assertEquals(
                new BigDecimal("190.00"),
                orderRow.get("price")
        );

        assertEquals(
                "NEW",
                orderRow.get("status")
        );

        assertEquals(
                "char-affordable-001",
                orderRow.get("idempotency_key")
        );


        // Account version increments because buying power is reserved at placement.
         assertEquals(
                2,
                accountVersion(1001L)
        );
    }


    // Mandated by the README:
    // Pin the existing duplicate-idempotency behaviour.
    @Test
    void reused_idempotency_key_pins_current_error() {

        PlaceOrderCommand command =
                buyCommand(
                        "idem-existing-1",
                        3,
                        "190.00"
                );


        DuplicateOrderException exception =
                assertThrows(
                        DuplicateOrderException.class,
                        () -> orderService.placeOrder(command)
                );


        assertEquals(
                "ORD-409",
                exception.getCode()
        );


        assertEquals(
                1,
                orderCountByIdempotency(
                        "idem-existing-1"
                )
        );
    }


    // Mandated by the README:
    // Pin the existing unaffordable BUY behaviour.
    @Test
    void unaffordable_buy_pins_current_error_and_no_state_change() {

        PlaceOrderCommand command =
                buyCommand(
                        "char-unaffordable-001",
                        1000,
                        "190.00"
                );


        InsufficientFundsException exception =
                assertThrows(
                        InsufficientFundsException.class,
                        () -> orderService.placeOrder(command)
                );


        assertEquals(
                "ORD-400",
                exception.getCode()
        );


        assertEquals(
                new BigDecimal("100000.00"),
                cashBalance(1001L)
        );

        assertEquals(
                new BigDecimal("100000.00"),
                buyingPower(1001L)
        );

        assertEquals(
                15,
                positionQuantity(
                        1001L,
                        "AAPL"
                )
        );

        assertEquals(
                new BigDecimal("171.50"),
                positionAverageCost(
                        1001L,
                        "AAPL"
                )
        );

        assertEquals(
                0,
                orderCountByIdempotency(
                        "char-unaffordable-001"
                )
        );
    }


    // Mandated by the README:
    // Pin the existing unknown-symbol behaviour.
    @Test
    void unknown_symbol_pins_current_error() {

        PlaceOrderCommand command =
                buyCommand(
                        "char-unknown-symbol-001",
                        5,
                        "190.00"
                );

        command.setSymbol("UNKNOWN");


        InstrumentNotFoundException exception =
                assertThrows(
                        InstrumentNotFoundException.class,
                        () -> orderService.placeOrder(command)
                );


        assertEquals(
                "INS-404",
                exception.getCode()
        );


        assertEquals(
                0,
                orderCountByIdempotency(
                        "char-unknown-symbol-001"
                )
        );
    }


    // Mandated by the README:
    // Pin the existing non-ACTIVE account behaviour.
    @Test
    void non_active_account_pins_current_error_and_no_state_change() {

        AuthContextHolder.setAccountId(1002L);


        PlaceOrderCommand command =
                buyCommand(
                        "char-inactive-account-001",
                        5,
                        "190.00"
                );

        command.setAccountId(1002L);


        AccountNotActiveException exception =
                assertThrows(
                        AccountNotActiveException.class,
                        () -> orderService.placeOrder(command)
                );


        assertEquals(
                "ACC-403",
                exception.getCode()
        );


        assertEquals(
                0,
                orderCountByIdempotency(
                        "char-inactive-account-001"
                )
        );
    }


    // Proactive based on how and why they are added:
    // Characterise the existing SELL path separately from BUY.
    @Test
    void affordable_sell_pins_current_response_and_position_change() {

        PlaceOrderCommand command =
                sellCommand(
                        "char-sell-001",
                        5,
                        "190.00"
                );


        OrderResponse response =
                orderService.placeOrder(command);


        assertNotNull(response.getOrderId());

        assertEquals(
                OrderStatus.NEW,
                response.getStatus()
        );

        assertEquals(
                "Order accepted",
                response.getMessage()
        );

        assertEquals(
                "AAPL",
                response.getSymbol()
        );

        assertEquals(
                OrderSide.SELL,
                response.getSide()
        );

        assertEquals(
                5,
                response.getQuantity()
        );

        assertEquals(
                new BigDecimal("190.00"),
                response.getPrice()
        );


        assertEquals(
                new BigDecimal("100000.00"),
                cashBalance(1001L)
        );

        assertEquals(
                new BigDecimal("100000.00"),
                buyingPower(1001L)
        );

        // Position should remain unchanged at placement (only updated at execution)
         assertEquals(
                15,
                positionQuantity(
                        1001L,
                        "AAPL"
                )
        );

        assertEquals(
                new BigDecimal("171.50"),
                positionAverageCost(
                        1001L,
                        "AAPL"
                )
        );

        assertEquals(
                1,
                orderCountByIdempotency(
                        "char-sell-001"
                )
        );
    }


    // Proactive based on how and why they are added:
    // Characterise the existing insufficient-holdings SELL path.
    @Test
    void sell_more_than_holdings_pins_current_error_and_no_state_change() {

        PlaceOrderCommand command =
                sellCommand(
                        "char-over-sell-001",
                        100,
                        "190.00"
                );


        InsufficientHoldingsException exception =
                assertThrows(
                        InsufficientHoldingsException.class,
                        () -> orderService.placeOrder(command)
                );


        assertEquals(
                "ORD-409",
                exception.getCode()
        );


        assertEquals(
                new BigDecimal("100000.00"),
                cashBalance(1001L)
        );

        assertEquals(
                new BigDecimal("100000.00"),
                buyingPower(1001L)
        );

        assertEquals(
                15,
                positionQuantity(
                        1001L,
                        "AAPL"
                )
        );

        assertEquals(
                new BigDecimal("171.50"),
                positionAverageCost(
                        1001L,
                        "AAPL"
                )
        );

        assertEquals(
                0,
                orderCountByIdempotency(
                        "char-over-sell-001"
                )
        );
    }


    // Proactive based on how and why they are added:
    // Characterise creation of a new position when none exists.
    @Test
    void buy_without_existing_position_does_not_create_position_until_execution() {

        PlaceOrderCommand command =
                buyCommand(
                        "char-new-position-001",
                        5,
                        "100.00"
                );

        command.setSymbol("MSFT");


        OrderResponse response =
                orderService.placeOrder(command);


        assertNotNull(response.getOrderId());

        assertEquals(
                OrderStatus.NEW,
                response.getStatus()
        );

        assertEquals(
                "Order accepted",
                response.getMessage()
        );

        assertEquals(
                "MSFT",
                response.getSymbol()
        );

        // Position should NOT be created at placement time (only at execution)
         assertEquals(
                0,
                positionCount(
                        1001L,
                        "MSFT"
                )
        );

        // Balance should NOT be deducted at placement time (only at execution)
         assertEquals(
                new BigDecimal("100000.00"),
                cashBalance(1001L)
        );

        assertEquals(
                new BigDecimal("99500.00"),
                buyingPower(1001L)
        );

        assertEquals(
                1,
                orderCountByIdempotency(
                        "char-new-position-001"
                )
        );
    }


    private PlaceOrderCommand buyCommand(
            String idempotencyKey,
            int quantity,
            String price
    ) {

        PlaceOrderCommand command =
                new PlaceOrderCommand();

        command.setAccountId(1001L);
        command.setSymbol("AAPL");
        command.setSide(OrderSide.BUY);
        command.setOrderType(OrderType.LIMIT);
        command.setQuantity(quantity);
        command.setPrice(new BigDecimal(price));
        command.setIdempotencyKey(idempotencyKey);

        return command;
    }


    private PlaceOrderCommand sellCommand(
            String idempotencyKey,
            int quantity,
            String price
    ) {

        PlaceOrderCommand command =
                new PlaceOrderCommand();

        command.setAccountId(1001L);
        command.setSymbol("AAPL");
        command.setSide(OrderSide.SELL);
        command.setOrderType(OrderType.LIMIT);
        command.setQuantity(quantity);
        command.setPrice(new BigDecimal(price));
        command.setIdempotencyKey(idempotencyKey);

        return command;
    }


    private BigDecimal cashBalance(Long accountId) {

        return jdbcTemplate.queryForObject(
                "SELECT cash_balance FROM accounts WHERE id = ?",
                BigDecimal.class,
                accountId
        );
    }


    private BigDecimal buyingPower(Long accountId) {

        return jdbcTemplate.queryForObject(
                "SELECT buying_power FROM accounts WHERE id = ?",
                BigDecimal.class,
                accountId
        );
    }


    private int accountVersion(Long accountId) {

        Integer version =
                jdbcTemplate.queryForObject(
                        "SELECT version FROM accounts WHERE id = ?",
                        Integer.class,
                        accountId
                );

        return version == null ? -1 : version;
    }


    private int positionQuantity(
            Long accountId,
            String symbol
    ) {

        Integer quantity =
                jdbcTemplate.queryForObject(
                        "SELECT qty FROM positions WHERE account_id = ? AND symbol = ?",
                        Integer.class,
                        accountId,
                        symbol
                );

        return quantity == null ? -1 : quantity;
    }


    private BigDecimal positionAverageCost(
            Long accountId,
            String symbol
    ) {

        return jdbcTemplate.queryForObject(
                "SELECT avg_cost FROM positions WHERE account_id = ? AND symbol = ?",
                BigDecimal.class,
                accountId,
                symbol
        );
    }


    private int positionCount(
            Long accountId,
            String symbol
    ) {

        Integer count =
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM positions WHERE account_id = ? AND symbol = ?",
                        Integer.class,
                        accountId,
                        symbol
                );

        return count == null ? -1 : count;
    }


    private int orderCountByIdempotency(
            String idempotencyKey
    ) {

        Integer count =
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM orders WHERE idempotency_key = ?",
                        Integer.class,
                        idempotencyKey
                );

        return count == null ? -1 : count;
    }
}