package com.tradingsystem.service.Impl;

import com.tradingsystem.dto.response.AccountResponse;
import com.tradingsystem.dto.response.BalanceResponse;
import com.tradingsystem.dto.response.PositionResponse;
import com.tradingsystem.exception.AccountNotFoundException;
import com.tradingsystem.mapper.AccountDataMapper;
import com.tradingsystem.model.Account;
import com.tradingsystem.model.Position;
import com.tradingsystem.service.AccountService;
import org.springframework.stereotype.Service;

import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

@Service
public class AccountServiceImpl implements AccountService {

    private final AccountDataMapper accountDataMapper;

    public AccountServiceImpl(AccountDataMapper accountDataMapper) {
        this.accountDataMapper = accountDataMapper;
    }

    @Override
    public AccountResponse getAccount(Long accountId) {
        Account account = accountDataMapper.findAccountById(accountId);
        if (account == null) {
            throw new AccountNotFoundException();
        }
        String holderName = accountDataMapper.findAccountHolderName(accountId);

        AccountResponse response = new AccountResponse();
        response.setId(account.getAccountId());
        response.setAccountId(account.getAccountReference());
        response.setHolderName(holderName);
        response.setCashBalance(account.getCashBalance());
        if (account.getStatus() != null) {
            response.setStatus(account.getStatus().name());
        }
        response.setVersion(account.getVersion());
        if (account.getLastUpdated() != null) {
            response.setLastUpdated(account.getLastUpdated().atOffset(ZoneOffset.UTC));
        }

        return response;
    }

    @Override
    public BalanceResponse getBalance(Long accountId) {
        Account account = accountDataMapper.findAccountById(accountId);
        if (account == null) {
            throw new AccountNotFoundException();
        }

        BalanceResponse response = new BalanceResponse();
        response.setAccountId(account.getAccountId());
        response.setCashBalance(account.getCashBalance());
        if (account.getLastUpdated() != null) {
            response.setAsOf(account.getLastUpdated().atOffset(ZoneOffset.UTC));
        }

        return response;
    }

    @Override
    public List<PositionResponse> getPositions(Long accountId) {
        List<Position> positions = accountDataMapper.findPositionsByAccountId(accountId);
        List<PositionResponse> responses = new ArrayList<>();
        for (Position position : positions) {
            PositionResponse response = new PositionResponse();
            response.setAccountId(position.getAccountId());
            response.setSymbol(position.getSymbol());
            response.setQuantity(position.getQuantity());
            response.setAverageCost(position.getAverageCost());
            responses.add(response);
        }
        return responses;
    }
}

