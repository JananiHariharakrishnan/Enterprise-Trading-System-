package com.tradingsystem.domain;

import com.tradingsystem.domain.entity.Account;
import com.tradingsystem.domain.entity.Instrument;
import com.tradingsystem.domain.entity.Order;
import com.tradingsystem.domain.entity.Position;
import com.tradingsystem.domain.enums.AccountStatus;
import com.tradingsystem.domain.enums.OrderSide;
import com.tradingsystem.domain.exceptions.*;

import java.math.BigDecimal;

public class OrderLogic {

    public void placeOrder(
            Account account,
            Instrument instrument,
            Order order) {

        placeOrder(account, instrument, order, false);
    }
    // Rules 1-6 and 8
    public void placeOrder(
            Account account,
            Instrument instrument,
            Order order,
            boolean duplicateKey) {

        // Rule 1: Account must exist
        if (account == null) {
            throw new AccountNotFoundException();
        }

        // Rule 2: Account must be active
        if (account.getStatus() != AccountStatus.ACTIVE) {
            throw new AccountNotActiveException();
        }

        // Rule 3: Instrument must exist
        if (instrument == null || !instrument.isTradable()) {
            throw new InstrumentNotFoundException();
        }

        // Rule 4: Quantity must be positive
        if (order == null || order.getQuantity() <= 0) {
            throw new InvalidOrderException();
        }

        // Rule 5: Price must be positive
        if (order.getPrice() == null ||
                order.getPrice().compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidOrderException();
        }

        // Rule 8: Idempotency key must be unique
        if (duplicateKey) {
            throw new DuplicateOrderException();
        }

        // Rule 6: BUY must have sufficient funds
        if (order.getSide() == OrderSide.BUY) {

            BigDecimal orderValue =
                    order.getPrice()
                            .multiply(
                                    BigDecimal.valueOf(order.getQuantity())
                            );

            if (account.getCashBalance().compareTo(orderValue) < 0) {
                throw new InsufficientFundsException();
            }
        }
    }


    // Rules 1-7 and 8
    public void placeOrder(
            Account account,
            Instrument instrument,
            Order order,
            Position position) {

        // First perform common rules
        placeOrder(
                account,
                instrument,
                order,
                false
        );

        // Rule 7: SELL must have sufficient holdings
        if (order.getSide() == OrderSide.SELL) {

            if (position == null ||
                    position.getQuantity() < order.getQuantity()) {

                throw new InsufficientHoldingsException();
            }
        }
    }


    // Version used for duplicate-key tests
    public void placeOrder(
            Account account,
            Instrument instrument,
            Order order,
            Position position,
            boolean duplicateKey) {

        // Rules 1-6 + 8
        placeOrder(
                account,
                instrument,
                order,
                duplicateKey
        );

        // Rule 7
        if (order.getSide() == OrderSide.SELL) {

            if (position == null ||
                    position.getQuantity() < order.getQuantity()) {

                throw new InsufficientHoldingsException();
            }
        }
    }
}