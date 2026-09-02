package com.tradingsystem.domain;

import com.tradingsystem.domain.entity.Account;
import com.tradingsystem.domain.entity.Instrument;
import com.tradingsystem.domain.entity.Order;
import com.tradingsystem.domain.entity.Position;
import com.tradingsystem.domain.enums.AccountStatus;
import com.tradingsystem.domain.enums.OrderSide;
import com.tradingsystem.domain.exceptions.*;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OrderLogicTest {

    @Test
    void rule1_throwsAccountNotFound_whenAccountDoesNotExist() {

        Instrument instrument = tradableInstrument("ACME");
        Order order = order(OrderSide.BUY, 100, "25.00", "key-12345");

        OrderLogic logic = new OrderLogic();

        AccountNotFoundException ex = assertThrows(
                AccountNotFoundException.class,
                () -> logic.placeOrder(
                        null,
                        instrument,
                        order
                )
        );

        assertEquals("ACC-404", ex.getCode());
    }


    @Test
    void rule2_throwsAccountNotActive_whenAccountIsSuspended() {

        Account account = activeAccount(10000);
        account.setStatus(AccountStatus.SUSPENDED);

        Instrument instrument = tradableInstrument("ACME");
        Order order = order(OrderSide.BUY, 100, "25.00", "key-12345");

        OrderLogic logic = new OrderLogic();

        AccountNotActiveException ex = assertThrows(
                AccountNotActiveException.class,
                () -> logic.placeOrder(
                        account,
                        instrument,
                        order
                )
        );

        assertEquals("ACC-403", ex.getCode());
    }

    @Test
    void rule2_throwsAccountNotActive_whenAccountIsClosed() {

        Account account = activeAccount(10000);
        account.setStatus(AccountStatus.CLOSED);

        Instrument instrument = tradableInstrument("ACME");
        Order order = order(OrderSide.BUY, 100, "25.00", "key-12345");

        OrderLogic logic = new OrderLogic();

        AccountNotActiveException ex = assertThrows(
                AccountNotActiveException.class,
                () -> logic.placeOrder(
                        account,
                        instrument,
                        order
                )
        );

        assertEquals("ACC-403", ex.getCode());
    }

    @Test
    void rule3_throwsInstrumentNotFound_whenInstrumentDoesNotExist() {

        Account account = activeAccount(10000);
        Order order = order(OrderSide.BUY, 100, "25.00", "key-12345");

        OrderLogic logic = new OrderLogic();

        InstrumentNotFoundException ex = assertThrows(
                InstrumentNotFoundException.class,
                () -> logic.placeOrder(
                        account,
                        null,
                        order
                )
        );

        assertEquals("INS-404", ex.getCode());
    }

    @Test
    void rule3_throwsInstrumentNotFound_whenInstrumentIsNotTradable() {

        Account account = activeAccount(10000);

        Instrument instrument = tradableInstrument("ACME");
        instrument.setTradable(false);

        Order order = order(OrderSide.BUY, 100, "25.00", "key-12345");

        OrderLogic logic = new OrderLogic();

        InstrumentNotFoundException ex = assertThrows(
                InstrumentNotFoundException.class,
                () -> logic.placeOrder(
                        account,
                        instrument,
                        order
                )
        );

        assertEquals("INS-404", ex.getCode());
    }

    @Test
    void rule4_throwsInvalidOrderException_whenQuantityIsZero() {

        Account account = activeAccount(10000);
        Instrument instrument = tradableInstrument("ACME");
        Order order = order(OrderSide.BUY, 0, "25.00", "key-12345");

        OrderLogic logic = new OrderLogic();

        InvalidOrderException ex = assertThrows(
                InvalidOrderException.class,
                () -> logic.placeOrder(
                        account,
                        instrument,
                        order
                )
        );

        assertEquals("VAL-422", ex.getCode());
    }

    @Test
    void rule4_throwsInvalidOrderException_whenQuantityIsNegative() {

        Account account = activeAccount(10000);
        Instrument instrument = tradableInstrument("ACME");
        Order order = order(OrderSide.BUY, -1, "25.00", "key-12345");

        OrderLogic logic = new OrderLogic();

        InvalidOrderException ex = assertThrows(
                InvalidOrderException.class,
                () -> logic.placeOrder(
                        account,
                        instrument,
                        order
                )
        );

        assertEquals("VAL-422", ex.getCode());
    }

    @Test
    void rule5_throwsInvalidOrderException_whenPriceIsZero() {

        Account account = activeAccount(10000);
        Instrument instrument = tradableInstrument("ACME");
        Order order = order(OrderSide.BUY, 100, "0.00", "key-12345");

        OrderLogic logic = new OrderLogic();

        InvalidOrderException ex = assertThrows(
                InvalidOrderException.class,
                () -> logic.placeOrder(
                        account,
                        instrument,
                        order
                )
        );

        assertEquals("VAL-422", ex.getCode());
    }

    @Test
    void rule5_throwsInvalidOrderException_whenPriceIsNegative() {

        Account account = activeAccount(10000);
        Instrument instrument = tradableInstrument("ACME");
        Order order = order(OrderSide.BUY, 100, "-1.00", "key-12345");

        OrderLogic logic = new OrderLogic();

        InvalidOrderException ex = assertThrows(
                InvalidOrderException.class,
                () -> logic.placeOrder(
                        account,
                        instrument,
                        order
                )
        );

        assertEquals("VAL-422", ex.getCode());
    }

    @Test
    void rule6_throwsInsufficientFunds_whenBuyBalanceIsTooLow() {

        Account account = activeAccount(1000);
        Instrument instrument = tradableInstrument("ACME");

        // 100 * 25 = 2500
        Order order = order(
                OrderSide.BUY,
                100,
                "25.00",
                "key-12345"
        );

        OrderLogic logic = new OrderLogic();

        InsufficientFundsException ex = assertThrows(
                InsufficientFundsException.class,
                () -> logic.placeOrder(
                        account,
                        instrument,
                        order
                )
        );

        assertEquals("ORD-400", ex.getCode());
    }

    @Test
    void rule6_buySucceedsWhenCashExactlyEqualsOrderValue() {

        Account account = activeAccount(2500);
        Instrument instrument = tradableInstrument("ACME");

        Order order = order(
                OrderSide.BUY,
                100,
                "25.00",
                "key-12345"
        );

        OrderLogic logic = new OrderLogic();

        assertDoesNotThrow(() ->
                logic.placeOrder(
                        account,
                        instrument,
                        order
                )
        );
    }

    @Test
    void rule7_throwsInsufficientHoldings_whenSellQuantityIsTooHigh() {

        Account account = activeAccount(1000);
        Instrument instrument = tradableInstrument("ACME");

        Position position = new Position();
        position.setAccountId(1L);
        position.setSymbol("ACME");
        position.setQuantity(50);

        Order order = order(
                OrderSide.SELL,
                100,
                "25.00",
                "key-12345"
        );

        OrderLogic logic = new OrderLogic();

        InsufficientHoldingsException ex = assertThrows(
                InsufficientHoldingsException.class,
                () -> logic.placeOrder(
                        account,
                        instrument,
                        order,
                        position
                )
        );

        assertEquals("ORD-409", ex.getCode());
    }

    @Test
    void rule7_sellSucceedsWhenHoldingsExactlyEqualQuantity() {

        Account account = activeAccount(1000);
        Instrument instrument = tradableInstrument("ACME");

        Position position = new Position();
        position.setAccountId(1L);
        position.setSymbol("ACME");
        position.setQuantity(100);

        Order order = order(
                OrderSide.SELL,
                100,
                "25.00",
                "key-12345"
        );

        OrderLogic logic = new OrderLogic();

        assertDoesNotThrow(() ->
                logic.placeOrder(
                        account,
                        instrument,
                        order,
                        position
                )
        );
    }


    @Test
    void rule8_throwsDuplicateOrderException_whenIdempotencyKeyAlreadyExists() {

        Account account = activeAccount(10000);
        Instrument instrument = tradableInstrument("ACME");

        Order order = order(
                OrderSide.BUY,
                100,
                "25.00",
                "key-12345"
        );

        OrderLogic logic = new OrderLogic();

        DuplicateOrderException ex = assertThrows(
                DuplicateOrderException.class,
                () -> logic.placeOrder(
                        account,
                        instrument,
                        order,
                        true
                )
        );

        assertEquals("ORD-409", ex.getCode());
    }

    // ---------------------------------------------------------
    // Rule Ordering
    // ---------------------------------------------------------

    @Test
    void firstRuleFailureWins() {

        // Account is missing.
        // Instrument is missing.
        // Quantity is invalid.
        // Price is invalid.
        //
        // Rule 1 must win.

        Order order = order(
                OrderSide.BUY,
                0,
                "0.00",
                "duplicate-key"
        );

        OrderLogic logic = new OrderLogic();

        AccountNotFoundException ex = assertThrows(
                AccountNotFoundException.class,
                () -> logic.placeOrder(
                        null,
                        null,
                        order,
                        true
                )
        );

        assertEquals("ACC-404", ex.getCode());
    }

    @Test
    void inactiveAccountWinsOverInsufficientFunds() {

        Account account = activeAccount(0);
        account.setStatus(AccountStatus.SUSPENDED);

        Instrument instrument = tradableInstrument("ACME");

        Order order = order(
                OrderSide.BUY,
                100,
                "25.00",
                "key-12345"
        );

        OrderLogic logic = new OrderLogic();

        AccountNotActiveException ex = assertThrows(
                AccountNotActiveException.class,
                () -> logic.placeOrder(
                        account,
                        instrument,
                        order
                )
        );

        assertEquals("ACC-403", ex.getCode());
    }

    // ---------------------------------------------------------
    // Helper Methods
    // ---------------------------------------------------------

    private Account activeAccount(double cash) {

        Account account = new Account();

        account.setStatus(AccountStatus.ACTIVE);
        account.setCashBalance(
                BigDecimal.valueOf(cash)
        );

        return account;
    }

    private Instrument tradableInstrument(String symbol) {

        Instrument instrument = new Instrument();

        instrument.setSymbol(symbol);
        instrument.setTradable(true);

        return instrument;
    }

    private Order order(
            OrderSide side,
            int quantity,
            String price,
            String idempotencyKey
    ) {

        Order order = new Order();

        order.setAccountId(1L);
        order.setSymbol("ACME");
        order.setSide(side);
        order.setQuantity(quantity);
        order.setPrice(new BigDecimal(price));
        order.setIdempotencyKey(idempotencyKey);

        return order;
    }
}