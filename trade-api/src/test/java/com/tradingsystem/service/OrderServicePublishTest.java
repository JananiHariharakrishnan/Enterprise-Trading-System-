package com.tradingsystem.service;

import com.tradingsystem.messaging.OrderEventPublisher;
import com.tradingsystem.security.AuthContextHolder;
import com.tradingsystem.service.command.PlaceOrderCommand;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;

import static org.mockito.Mockito.*;

@SpringBootTest(properties = {
        "auth.jwt.secret=test-secret-key-test-secret-key-test-secret-key",
        "app.kafka.orders-topic=orders"
})
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:publishtest;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password="
})
@Sql(
        scripts = {
                "/schema-order-service-test.sql",
                "/data-order-service-test.sql"
        },
        executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD
)
class OrderServicePublishTest {

    @Autowired
    private PlatformTransactionManager txManager;

    @Autowired
    private OrderService orderService;

    @MockitoBean
    private OrderEventPublisher orderEventPublisher;

    @BeforeEach
    void setupAuthentication() {
        AuthContextHolder.setAccountId(1001L);
    }

    @AfterEach
    void clearAuthentication() {
        AuthContextHolder.clear();
    }

    @Test
    void order_publish_happens_after_transaction_commit() {

        TransactionTemplate tx = new TransactionTemplate(txManager);

        PlaceOrderCommand command = new PlaceOrderCommand();
        command.setAccountId(1001L);
        command.setSymbol("AAPL");
        command.setSide(com.tradingsystem.enums.OrderSide.BUY);
        // LIMIT order with a price
        command.setOrderType(com.tradingsystem.enums.OrderType.LIMIT);
        command.setQuantity(1);
        command.setPrice(new BigDecimal("100.00"));
        command.setIdempotencyKey("publish-after-commit-1");

        tx.execute(status -> {
            orderService.placeOrder(command);

            // Publisher must not be called before transaction commit
            verify(orderEventPublisher, never())
                    .publishOrderPlaced(any());

            return null;
        });

        // Publisher must be called after successful commit
        verify(orderEventPublisher, times(1))
                .publishOrderPlaced(any());
    }

    @Test
    void validation_failure_publishes_nothing() {

        PlaceOrderCommand invalid = new PlaceOrderCommand();
        invalid.setAccountId(1001L);
        invalid.setSymbol("");
        invalid.setSide(com.tradingsystem.enums.OrderSide.BUY);
        invalid.setOrderType(com.tradingsystem.enums.OrderType.LIMIT);
        invalid.setQuantity(0);
        invalid.setIdempotencyKey("invalid-publish-1");

        try {
            orderService.placeOrder(invalid);
        } catch (Exception ignored) {
            // Expected validation or business exception
        }

        verify(orderEventPublisher, never())
                .publishOrderPlaced(any());
    }
}