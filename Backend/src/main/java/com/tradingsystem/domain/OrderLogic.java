package com.tradingsystem.domain;

import com.tradingsystem.domain.entity.Account;
import com.tradingsystem.domain.entity.Instrument;
import com.tradingsystem.domain.entity.Order;
import com.tradingsystem.domain.entity.Position;

import java.util.Set;

public class OrderLogic {

    public OrderResult validate(
            Account account,
            Instrument instrument,
            Order order,
            Set<String> usedKeys) {

        // TODO: Implement business rules
        return null;
    }

    public OrderResult validate(
            Account account,
            Instrument instrument,
            Order order,
            Set<String> usedKeys,
            Position position) {

        // TODO: Implement business rules
        return null;
    }
}