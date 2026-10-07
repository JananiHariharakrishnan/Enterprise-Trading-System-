package com.tradingsystem.exception;

/** The token's accountId claim does not match the account in the path. Mapped to 403 / ACC-403. */
public class AccountAccessDeniedException extends TradingDomainException {

    public AccountAccessDeniedException() {
        super("ACC-403", "Account not accessible");
    }
}
