package com.tradingsystem.domain;

import com.tradingsystem.domain.entity.Account;
import com.tradingsystem.domain.entity.Instrument;
import com.tradingsystem.domain.entity.Order;
import com.tradingsystem.domain.entity.Position;
import com.tradingsystem.domain.enums.AccountStatus;
import com.tradingsystem.domain.enums.OrderSide;

import java.math.BigDecimal;
import java.util.Set;

public class OrderLogic {

    public OrderResult validate(
            Account account,
            Instrument instrument,
            Order order,
            Set<String> usedKeys) {

        // Rule 1: Account must exist
        if (account == null) {
            return OrderResult.failure("ACC-404");
        }

        // Rule 2: Account must be active
        if (account.getStatus() != AccountStatus.ACTIVE) {
            return OrderResult.failure("ACC-403");
        }

        // Rule 3: Instrument must exist
        if (instrument == null) {
            return OrderResult.failure("INS-404");
        }

        // Rule 4: Instrument must be tradable
        if (!instrument.isTradable()) {
            return OrderResult.failure("INS-404");
        }

        // Rule 5: Order must exist
        if (order == null) {
            return OrderResult.failure("VAL-422");
        }

        // Rule 6: Quantity must be positive
        if (order.getQuantity() <= 0) {
            return OrderResult.failure("VAL-422");
        }

        // Rule 7: Price must be positive
        if (order.getPrice() == null ||
                order.getPrice().compareTo(BigDecimal.ZERO) <= 0) {
            return OrderResult.failure("VAL-422");
        }

        // Rule 8: Idempotency key must not already be used
        if (usedKeys != null &&
                usedKeys.contains(order.getIdempotencyKey())) {
            return OrderResult.failure("ORD-409");
        }

        // Rule 9: BUY order requires sufficient cash
        if (order.getSide() == OrderSide.BUY) {

            BigDecimal orderValue =
                    order.getPrice().multiply(
                            BigDecimal.valueOf(order.getQuantity())
                    );

            if (account.getCashBalance().compareTo(orderValue) < 0) {
                return OrderResult.failure("ORD-400");
            }
        }

        return OrderResult.success();
    }


    public OrderResult validate(
            Account account,
            Instrument instrument,
            Order order,
            Set<String> usedKeys,
            Position position) {

        // Apply the common validation rules first
        OrderResult result = validate(
                account,
                instrument,
                order,
                usedKeys
        );

        if (!result.isSuccess()) {
            return result;
        }

        // SELL orders require sufficient holdings
        if (order.getSide() == OrderSide.SELL) {

            if (position == null ||
                    position.getQuantity() < order.getQuantity()) {
                return OrderResult.failure("ORD-409");
            }
        }

        return OrderResult.success();
    }
}