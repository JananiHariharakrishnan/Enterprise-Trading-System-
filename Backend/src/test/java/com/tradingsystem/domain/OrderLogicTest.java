package com.tradingsystem.domain;

import com.tradingsystem.domain.entity.Account;
import com.tradingsystem.domain.entity.Instrument;
import com.tradingsystem.domain.entity.Order;
import com.tradingsystem.domain.entity.Position;
import com.tradingsystem.domain.enums.AccountStatus;
import com.tradingsystem.domain.enums.OrderSide;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class OrderLogicTest {

    @Test
    void buyOrderSucceedsWhenAllBusinessRulesPass() {
        Account account = activeAccount(10000);
        Instrument instrument = tradableInstrument("ACME");
        Order order = order(OrderSide.BUY, 100, "25.00", "key-12345");

        OrderLogic logic = new OrderLogic();

        OrderResult result = logic.validate(
                account,
                instrument,
                order,
                new HashSet<>()
        );

        assertTrue(result.isSuccess());
    }

    @Test
    void missingAccountIsRejectedWithAcc404() {
        Instrument instrument = tradableInstrument("ACME");
        Order order = order(OrderSide.BUY, 100, "25.00", "key-12345");

        OrderLogic logic = new OrderLogic();

        OrderResult result = logic.validate(
                null,
                instrument,
                order,
                new HashSet<>()
        );

        assertFalse(result.isSuccess());
        assertEquals("ACC-404", result.getErrorCode());
    }

    @Test
    void suspendedAccountIsRejectedWithAcc403() {
        Account account = activeAccount(10000);
        account.setStatus(AccountStatus.SUSPENDED);

        Instrument instrument = tradableInstrument("ACME");
        Order order = order(OrderSide.BUY, 100, "25.00", "key-12345");

        OrderLogic logic = new OrderLogic();

        OrderResult result = logic.validate(
                account,
                instrument,
                order,
                new HashSet<>()
        );

        assertFalse(result.isSuccess());
        assertEquals("ACC-403", result.getErrorCode());
    }

    @Test
    void closedAccountIsRejectedWithAcc403() {
        Account account = activeAccount(10000);
        account.setStatus(AccountStatus.CLOSED);

        Instrument instrument = tradableInstrument("ACME");
        Order order = order(OrderSide.BUY, 100, "25.00", "key-12345");

        OrderLogic logic = new OrderLogic();

        OrderResult result = logic.validate(
                account,
                instrument,
                order,
                new HashSet<>()
        );

        assertFalse(result.isSuccess());
        assertEquals("ACC-403", result.getErrorCode());
    }

    @Test
    void unknownInstrumentIsRejectedWithIns404() {
        Account account = activeAccount(10000);
        Order order = order(OrderSide.BUY, 100, "25.00", "key-12345");

        OrderLogic logic = new OrderLogic();

        OrderResult result = logic.validate(
                account,
                null,
                order,
                new HashSet<>()
        );

        assertFalse(result.isSuccess());
        assertEquals("INS-404", result.getErrorCode());
    }

    @Test
    void nonTradableInstrumentIsRejectedWithIns404() {
        Account account = activeAccount(10000);
        Instrument instrument = tradableInstrument("ACME");
        instrument.setTradable(false);

        Order order = order(OrderSide.BUY, 100, "25.00", "key-12345");

        OrderLogic logic = new OrderLogic();

        OrderResult result = logic.validate(
                account,
                instrument,
                order,
                new HashSet<>()
        );

        assertFalse(result.isSuccess());
        assertEquals("INS-404", result.getErrorCode());
    }

    @Test
    void zeroQuantityIsRejectedWithVal422() {
        Account account = activeAccount(10000);
        Instrument instrument = tradableInstrument("ACME");
        Order order = order(OrderSide.BUY, 0, "25.00", "key-12345");

        OrderLogic logic = new OrderLogic();

        OrderResult result = logic.validate(
                account,
                instrument,
                order,
                new HashSet<>()
        );

        assertFalse(result.isSuccess());
        assertEquals("VAL-422", result.getErrorCode());
    }

    @Test
    void negativeQuantityIsRejectedWithVal422() {
        Account account = activeAccount(10000);
        Instrument instrument = tradableInstrument("ACME");
        Order order = order(OrderSide.BUY, -1, "25.00", "key-12345");

        OrderLogic logic = new OrderLogic();

        OrderResult result = logic.validate(
                account,
                instrument,
                order,
                new HashSet<>()
        );

        assertFalse(result.isSuccess());
        assertEquals("VAL-422", result.getErrorCode());
    }

    @Test
    void zeroPriceIsRejectedWithVal422() {
        Account account = activeAccount(10000);
        Instrument instrument = tradableInstrument("ACME");
        Order order = order(OrderSide.BUY, 100, "0.00", "key-12345");

        OrderLogic logic = new OrderLogic();

        OrderResult result = logic.validate(
                account,
                instrument,
                order,
                new HashSet<>()
        );

        assertFalse(result.isSuccess());
        assertEquals("VAL-422", result.getErrorCode());
    }

    @Test
    void negativePriceIsRejectedWithVal422() {
        Account account = activeAccount(10000);
        Instrument instrument = tradableInstrument("ACME");
        Order order = order(OrderSide.BUY, 100, "-1.00", "key-12345");

        OrderLogic logic = new OrderLogic();

        OrderResult result = logic.validate(
                account,
                instrument,
                order,
                new HashSet<>()
        );

        assertFalse(result.isSuccess());
        assertEquals("VAL-422", result.getErrorCode());
    }

    @Test
    void buyIsRejectedWhenCashIsInsufficient() {
        Account account = activeAccount(1000);
        Instrument instrument = tradableInstrument("ACME");
        Order order = order(OrderSide.BUY, 100, "25.00", "key-12345");

        OrderLogic logic = new OrderLogic();

        OrderResult result = logic.validate(
                account,
                instrument,
                order,
                new HashSet<>()
        );

        assertFalse(result.isSuccess());
        assertEquals("ORD-400", result.getErrorCode());
    }

    @Test
    void buyIsAcceptedWhenCashExactlyEqualsOrderValue() {
        Account account = activeAccount(2500);
        Instrument instrument = tradableInstrument("ACME");
        Order order = order(OrderSide.BUY, 100, "25.00", "key-12345");

        OrderLogic logic = new OrderLogic();

        OrderResult result = logic.validate(
                account,
                instrument,
                order,
                new HashSet<>()
        );

        assertTrue(result.isSuccess());
    }

    @Test
    void sellIsRejectedWhenHoldingsAreInsufficient() {
        Account account = activeAccount(1000);
        Instrument instrument = tradableInstrument("ACME");

        Position position = new Position();
        position.setAccountId(1L);
        position.setSymbol("ACME");
        position.setQuantity(50);

        Order order = order(OrderSide.SELL, 100, "25.00", "key-12345");

        OrderLogic logic = new OrderLogic();

        OrderResult result = logic.validate(
                account,
                instrument,
                order,
                new HashSet<>(),
                position
        );

        assertFalse(result.isSuccess());
        assertEquals("ORD-409", result.getErrorCode());
    }

    @Test
    void sellIsAcceptedWhenHoldingsExactlyEqualOrderQuantity() {
        Account account = activeAccount(1000);
        Instrument instrument = tradableInstrument("ACME");

        Position position = new Position();
        position.setAccountId(1L);
        position.setSymbol("ACME");
        position.setQuantity(100);

        Order order = order(OrderSide.SELL, 100, "25.00", "key-12345");

        OrderLogic logic = new OrderLogic();

        OrderResult result = logic.validate(
                account,
                instrument,
                order,
                new HashSet<>(),
                position
        );

        assertTrue(result.isSuccess());
    }

    @Test
    void duplicateIdempotencyKeyIsRejected() {
        Account account = activeAccount(10000);
        Instrument instrument = tradableInstrument("ACME");
        Order order = order(OrderSide.BUY, 100, "25.00", "key-12345");

        Set<String> usedKeys = new HashSet<>();
        usedKeys.add("key-12345");

        OrderLogic logic = new OrderLogic();

        OrderResult result = logic.validate(
                account,
                instrument,
                order,
                usedKeys
        );

        assertFalse(result.isSuccess());
        assertEquals("ORD-409", result.getErrorCode());
    }

    @Test
    void firstRuleFailureWins() {
        // Rule 1 must win even though every later rule also fails.
        Order order = order(OrderSide.BUY, 0, "0.00", "duplicate-key");

        Set<String> usedKeys = new HashSet<>();
        usedKeys.add("duplicate-key");

        OrderLogic logic = new OrderLogic();

        OrderResult result = logic.validate(
                null,
                null,
                order,
                usedKeys
        );

        assertFalse(result.isSuccess());
        assertEquals("ACC-404", result.getErrorCode());
    }

    @Test
    void inactiveAccountWinsOverInsufficientFunds() {
        // Explicit acceptance criterion:
        // suspended account + no cash -> ACC-403, not ORD-400.
        Account account = activeAccount(0);
        account.setStatus(AccountStatus.SUSPENDED);

        Instrument instrument = tradableInstrument("ACME");
        Order order = order(OrderSide.BUY, 100, "25.00", "key-12345");

        OrderLogic logic = new OrderLogic();

        OrderResult result = logic.validate(
                account,
                instrument,
                order,
                new HashSet<>()
        );

        assertFalse(result.isSuccess());
        assertEquals("ACC-403", result.getErrorCode());
    }

    private Account activeAccount(double cash) {
        Account account = new Account();
        account.setStatus(AccountStatus.ACTIVE);
        account.setCashBalance(BigDecimal.valueOf(cash));
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