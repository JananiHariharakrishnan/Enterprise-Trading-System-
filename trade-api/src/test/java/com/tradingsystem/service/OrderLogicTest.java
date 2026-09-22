package com.tradingsystem.service;

import com.tradingsystem.enums.AccountStatus;
import com.tradingsystem.enums.OrderSide;
import com.tradingsystem.exception.*;
import com.tradingsystem.model.Account;
import com.tradingsystem.model.Instrument;
import com.tradingsystem.model.Order;
import com.tradingsystem.model.Position;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertThrows;

class OrderLogicTest {

    private final OrderLogic logic = new OrderLogic();

    @Test
    void placeOrder_nullAccount_throwsAccountNotFound() {
        Instrument instrument = new Instrument();
        Order order = new Order();
        assertThrows(AccountNotFoundException.class, () ->
                logic.placeOrder(null, instrument, order)
        );
    }

    @Test
    void placeOrder_inactiveAccount_throwsAccountNotActive() {
        Instrument instrument = new Instrument();
        Order order = new Order();
        Account a = new Account();
        a.setStatus(AccountStatus.SUSPENDED);
        assertThrows(AccountNotActiveException.class, () ->
                logic.placeOrder(a, instrument, order)
        );
    }

    @Test
    void placeOrder_nullInstrument_throwsInstrumentNotFound() {
        Account a = new Account();
        a.setStatus(AccountStatus.ACTIVE);
        a.setCashBalance(BigDecimal.TEN);
        Order o = new Order();
        o.setQuantity(1);
        o.setPrice(BigDecimal.ONE);
        o.setSide(OrderSide.BUY);
        assertThrows(InstrumentNotFoundException.class, () ->
                logic.placeOrder(a, null, o)
        );
    }

    @Test
    void placeOrder_invalidQuantity_throwsInvalidOrder() {
        Account a = new Account();
        a.setStatus(AccountStatus.ACTIVE);
        a.setCashBalance(BigDecimal.TEN);
        Instrument inst = new Instrument();
        inst.setTradable(true);
        Order o = new Order();
        o.setQuantity(0);
        o.setPrice(BigDecimal.ONE);
        assertThrows(InvalidOrderException.class, () ->
                logic.placeOrder(a, inst, o)
        );
    }

    @Test
    void placeOrder_insufficientFunds_throwsInsufficientFunds() {
        Account a = new Account();
        a.setStatus(AccountStatus.ACTIVE);
        a.setCashBalance(new BigDecimal("1.00"));
        Instrument inst = new Instrument();
        inst.setTradable(true);
        Order o = new Order();
        o.setQuantity(10);
        o.setPrice(new BigDecimal("1.00"));
        o.setSide(OrderSide.BUY);
        assertThrows(InsufficientFundsException.class, () ->
                logic.placeOrder(a, inst, o)
        );
    }

    @Test
    void placeOrder_sellWithoutPosition_throwsInsufficientHoldings() {
        Account a = new Account();
        a.setStatus(AccountStatus.ACTIVE);
        a.setCashBalance(BigDecimal.TEN);
        Instrument inst = new Instrument();
        inst.setTradable(true);
        Order o = new Order();
        o.setQuantity(5);
        o.setPrice(BigDecimal.ONE);
        o.setSide(OrderSide.SELL);
        assertThrows(InsufficientHoldingsException.class, () ->
                logic.placeOrder(a, inst, o, (Position) null)
        );
    }
}
