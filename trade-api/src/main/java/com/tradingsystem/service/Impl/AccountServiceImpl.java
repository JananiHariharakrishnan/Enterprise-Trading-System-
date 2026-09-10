package com.tradingsystem.service.Impl;

import com.tradingsystem.dto.response.AccountResponse;
import com.tradingsystem.dto.response.BalanceResponse;
import com.tradingsystem.dto.response.PositionResponse;
import com.tradingsystem.exception.AccountNotActiveException;
import com.tradingsystem.exception.AccountNotFoundException;
import com.tradingsystem.exception.TradingAuthenticationException;
import com.tradingsystem.mapper.AccountDataMapper;
import com.tradingsystem.model.Account;
import com.tradingsystem.model.Position;
import com.tradingsystem.security.CurrentUser;
import com.tradingsystem.service.AccountService;
import org.springframework.stereotype.Service;

import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

@Service
public class AccountServiceImpl implements AccountService {

    private final AccountDataMapper accountDataMapper;
    private final CurrentUser currentUser;

    public AccountServiceImpl(
            AccountDataMapper accountDataMapper,
            CurrentUser currentUser) {

        this.accountDataMapper = accountDataMapper;
        this.currentUser = currentUser;
    }

    @Override
    public AccountResponse getAccount(Long accountId) {

        Account account = getAuthorizedAccount(accountId);

        String holderName =
                accountDataMapper.findAccountHolderName(accountId);

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
            response.setLastUpdated(
                    account.getLastUpdated().atOffset(ZoneOffset.UTC)
            );
        }

        return response;
    }

    @Override
    public BalanceResponse getBalance(Long accountId) {

        Account account = getAuthorizedAccount(accountId);

        BalanceResponse response = new BalanceResponse();

        response.setAccountId(account.getAccountId());
        response.setCashBalance(account.getCashBalance());

        /*
         * IMPORTANT:
         * The current accounts table does not contain a currency column.
         * Do not hard-code USD here unless the team's contract explicitly
         * establishes USD as the account base currency.
         *
         * See the currency note below.
         */

        if (account.getLastUpdated() != null) {
            response.setAsOf(
                    account.getLastUpdated().atOffset(ZoneOffset.UTC)
            );
        }

        return response;
    }

    @Override
    public List<PositionResponse> getPositions(Long accountId) {

        /*
         * This deliberately verifies account existence BEFORE querying
         * positions.
         *
         * Otherwise an unknown account could incorrectly return [] instead
         * of ACC-404.
         */
        Account account = getAuthorizedAccount(accountId);

        List<Position> positions =
                accountDataMapper.findPositionsByAccountId(
                        account.getAccountId()
                );

        List<PositionResponse> responses =
                new ArrayList<>();

        for (Position position : positions) {

            PositionResponse response =
                    new PositionResponse();

            response.setAccountId(position.getAccountId());
            response.setSymbol(position.getSymbol());
            response.setQuantity(position.getQuantity());
            response.setAverageCost(position.getAverageCost());

            responses.add(response);
        }

        return responses;
    }

    /**
     * Resolves the account and verifies all account-level access rules.
     *
     * Execution order:
     *
     * 1. Account does not exist -> ACC-404
     * 2. Account is inactive -> ACC-403
     * 3. Token does not reach account -> ACC-403
     * 4. Otherwise return account
     */
    private Account getAuthorizedAccount(Long accountId) {

        // EDGE CASE FIX: Validate account ID is not null
        if (accountId == null || accountId <= 0) {
            throw new AccountNotFoundException();
        }

        Account account =
                accountDataMapper.findAccountById(accountId);

        if (account == null) {
            throw new AccountNotFoundException();
        }

        if (!account.isActive()) {
            throw new AccountNotActiveException();
        }

        Long authenticatedAccountId =
                currentUser.getAccountId();

        // EDGE CASE FIX: Better handling of missing authentication context
        if (authenticatedAccountId == null) {
            throw new TradingAuthenticationException("No authentication context");
        }

        if (!accountId.equals(authenticatedAccountId)) {

            throw new AccountNotActiveException();
        }

        return account;
    }
}