package com.tradingsystem.service;

import com.tradingsystem.enums.AccountStatus;
import com.tradingsystem.enums.OrderSide;
import com.tradingsystem.exception.AccountNotActiveException;
import com.tradingsystem.exception.AccountNotFoundException;
import com.tradingsystem.exception.DuplicateOrderException;
import com.tradingsystem.exception.InsufficientFundsException;
import com.tradingsystem.exception.InsufficientHoldingsException;
import com.tradingsystem.exception.InstrumentNotFoundException;
import com.tradingsystem.exception.InvalidOrderException;
import com.tradingsystem.model.Account;
import com.tradingsystem.model.Instrument;
import com.tradingsystem.model.Order;
import com.tradingsystem.model.Position;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
public class OrderLogic {

    // Rules 1-6
    public void placeOrder(
            Account account,
            Instrument instrument,
            Order order
    ) {
        placeOrder(
                account,
                instrument,
                order,
                false
        );
    }

    // Rules 1-6 and 8
    public void placeOrder(
            Account account,
            Instrument instrument,
            Order order,
            boolean duplicateKey
    ) {
        validateAccount(account);
        validateInstrument(instrument);
        validateOrder(order);

        if (duplicateKey) {
            throw new DuplicateOrderException();
        }

        validateBuyOrder(account, order);
    }

    private void validateAccount(Account account) {
        // Rule 1: Account must exist
        if (account == null) {
            throw new AccountNotFoundException();
        }

        // Rule 2: Account must be active
        if (account.getStatus() != AccountStatus.ACTIVE) {
            throw new AccountNotActiveException();
        }

        // Rule 2.5: Account cash balance must be valid
        // EDGE CASE FIX: Verify cash balance is set
        if (account.getCashBalance() == null) {
            throw new AccountNotActiveException();
        }
    }

    private void validateInstrument(Instrument instrument) {
        // Rule 3: Instrument must exist and be tradable
        // EDGE CASE FIX: Handle null instrument check
        if (instrument == null) {
            throw new InstrumentNotFoundException();
        }

        if (!instrument.isTradable()) {
            throw new InstrumentNotFoundException();
        }
    }

    private void validateOrder(Order order) {
        // Rule 4: Quantity must be positive
        if (order == null || order.getQuantity() <= 0) {
            throw new InvalidOrderException();
        }

        // Rule 5: Price must be positive
        if (order.getPrice() == null || order.getPrice().compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidOrderException();
        }
    }

    private void validateBuyOrder(Account account, Order order) {
        // Rule 6: BUY requires sufficient balance
        if (order.getSide() == OrderSide.BUY) {
            // EDGE CASE FIX: Validate quantity doesn't cause overflow
            if (order.getQuantity() > Integer.MAX_VALUE / 1000) {
                throw new InvalidOrderException();
            }

            BigDecimal orderValue = order.getPrice().multiply(BigDecimal.valueOf(order.getQuantity()));

            // EDGE CASE FIX: Check price precision
            if (orderValue.scale() > 10) {
                throw new InvalidOrderException();
            }

            if (account.getCashBalance().compareTo(orderValue) < 0) {
                throw new InsufficientFundsException();
            }
        }
    }

    // Rules 1-7
    public void placeOrder(
            Account account,
            Instrument instrument,
            Order order,
            Position position
    ) {
        // Validate common rules
        placeOrder(
                account,
                instrument,
                order,
                false
        );

        // Rule 7: SELL requires holdings
        if (order.getSide() == OrderSide.SELL
                && (position == null
                || position.getQuantity() < order.getQuantity())) {
            throw new InsufficientHoldingsException();
        }
    }

    // Rules 1-8
    public void placeOrder(
            Account account,
            Instrument instrument,
            Order order,
            Position position,
            boolean duplicateKey
    ) {
        // Validate rules 1-6 + 8
        placeOrder(
                account,
                instrument,
                order,
                duplicateKey
        );

        // Rule 7
        if (order.getSide() == OrderSide.SELL && (position == null || position.getQuantity() < order.getQuantity())) {
            throw new InsufficientHoldingsException();
        }
    }
}