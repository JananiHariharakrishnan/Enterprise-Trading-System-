package com.tradingsystem.service;

import com.tradingsystem.dto.response.OrderResponse;
import com.tradingsystem.enums.OrderSide;
import com.tradingsystem.enums.OrderType;
import com.tradingsystem.exception.DuplicateOrderException;
import com.tradingsystem.exception.OrderStateConflictException;
import com.tradingsystem.mapper.OrderDataMapper;
import com.tradingsystem.security.AuthContextHolder;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;


@SpringBootTest
@TestPropertySource(properties = {

        "spring.datasource.url=jdbc:h2:mem:orderservicetests;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
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
class OrderServiceTransactionalIntegrationTest {


    @Autowired
    private OrderService orderService;


    @Autowired
    private OrderDataMapper orderDataMapper;


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



    @Test
    void place_order_commits_cash_and_position_together() {

        PlaceOrderCommand command =
                buyCommand(
                        "idem-buy-commit-1",
                        5,
                        "190.00"
                );


        OrderResponse response =
                orderService.placeOrder(command);


        assertEquals(
                "Order accepted",
                response.getMessage()
        );

        // Balance is NOT updated at placement - only at execution
         assertEquals(
                new BigDecimal("100000.00"),
                cashBalance(1001L)
        );

        // Position and account state remain unchanged at placement
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

        // Account version increments because buying power is reserved at placement.
         assertEquals(
                2,
                accountVersion(1001L)
        );


        assertEquals(
                1,
                orderCountByIdempotency(
                        "idem-buy-commit-1"
                )
        );

    }



    @Test
    void failed_order_rolls_back_with_no_partial_write() {

        PlaceOrderCommand command =
                buyCommand(
                        "idem-existing-1",
                        3,
                        "190.00"
                );


        assertThrows(
                DuplicateOrderException.class,
                () -> orderService.placeOrder(command)
        );


        assertEquals(
                new BigDecimal("100000.00"),
                cashBalance(1001L)
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
                1,
                accountVersion(1001L)
        );


        assertEquals(
                1,
                orderCountByIdempotency(
                        "idem-existing-1"
                )
        );

    }



    @Test
    void concurrent_update_is_detected_and_second_writer_is_refused() {


        int first =
                orderDataMapper.updateAccountBalancesWithVersion(
                        1001L,
                        new BigDecimal("99999.00"),
                        new BigDecimal("99999.00"),
                        1
                );


        int second =
                orderDataMapper.updateAccountBalancesWithVersion(
                        1001L,
                        new BigDecimal("99998.00"),
                        new BigDecimal("99998.00"),
                        1
                );


        assertEquals(
                1,
                first
        );


        assertEquals(
                0,
                second
        );

    }



    @Test
    void cancelling_a_filled_order_returns_ord_409() {


        OrderStateConflictException exception =
                assertThrows(
                        OrderStateConflictException.class,
                        () ->
                                orderService.cancelOrder(
                                        "22222222-2222-2222-2222-222222222222"
                                )
                );


        assertEquals(
                "ORD-409",
                exception.getCode()
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



    private BigDecimal cashBalance(Long accountId) {

        return jdbcTemplate.queryForObject(
                "SELECT cash_balance FROM accounts WHERE id = ?",
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