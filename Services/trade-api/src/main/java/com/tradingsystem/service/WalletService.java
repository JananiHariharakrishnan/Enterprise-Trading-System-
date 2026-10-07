package com.tradingsystem.service;

import com.tradingsystem.dto.request.WalletDepositRequest;
import com.tradingsystem.dto.response.WalletDepositResponse;
import com.tradingsystem.model.Account;

public interface WalletService {

    /**
     * Retrieve current wallet and balance information for the authenticated user's account.
     */
    Account getWalletDetails(Long accountId);

    /**
     * Verify the user's password and deposit additional funds into their account.
     * Records transaction in balance_history.
     */
    WalletDepositResponse depositFunds(Long accountId, WalletDepositRequest request);
}
