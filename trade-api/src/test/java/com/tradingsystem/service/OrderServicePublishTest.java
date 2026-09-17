package com.tradingsystem.service;

import com.tradingsystem.messaging.OrderEventPublisher;
import com.tradingsystem.service.command.PlaceOrderCommand;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.mockito.Mockito.*;

@SpringBootTest
class OrderServicePublishTest {

    @Autowired
    private PlatformTransactionManager txManager;

    @Autowired
    private OrderService orderService;

    @MockitoBean
    private OrderEventPublisher orderEventPublisher;

    @Test
    void order_publish_happens_after_transaction_commit() {

        TransactionTemplate tx = new TransactionTemplate(txManager);

        PlaceOrderCommand command = new PlaceOrderCommand();
        command.setAccountId(1001L);
        command.setSymbol("AAPL");
        command.setSide(com.tradingsystem.enums.OrderSide.BUY);
        // MARKET order - price intentionally not provided
        command.setOrderType(com.tradingsystem.enums.OrderType.MARKET);
        command.setQuantity(1);
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