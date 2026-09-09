
package com.tradingsystem.service;

import com.tradingsystem.dto.response.AccountResponse;
import com.tradingsystem.dto.response.BalanceResponse;
import com.tradingsystem.dto.response.PositionResponse;

import java.util.List;

public interface AccountService {

    AccountResponse getAccount(Long accountId);

    BalanceResponse getBalance(Long accountId);

    List<PositionResponse> getPositions(Long accountId);
}

