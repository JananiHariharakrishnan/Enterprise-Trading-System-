
package com.tradingsystem.service.Impl;

import com.tradingsystem.dto.response.AccountResponse;
import com.tradingsystem.dto.response.BalanceResponse;
import com.tradingsystem.dto.response.PositionResponse;
import com.tradingsystem.service.AccountService;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

@Service
public class AccountServiceImpl implements AccountService {

    @Override
    public AccountResponse getAccount(Long accountId) {

        AccountResponse response = new AccountResponse();
        response.setId(accountId);
        response.setAccountId(String.valueOf(accountId));

        return response;
    }

    @Override
    public BalanceResponse getBalance(Long accountId) {

        BalanceResponse response = new BalanceResponse();
        response.setAccountId(accountId);

        return response;
    }

    @Override
    public List<PositionResponse> getPositions(Long accountId) {

        return Collections.emptyList();
    }
}

