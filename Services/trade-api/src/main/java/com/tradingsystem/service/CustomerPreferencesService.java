package com.tradingsystem.service;

import com.tradingsystem.mapper.CustomerPreferencesDataMapper;
import com.tradingsystem.model.CustomerPreferences;
import com.tradingsystem.model.Account;
import com.tradingsystem.exception.AccountNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CustomerPreferencesService {

    private final CustomerPreferencesDataMapper mapper;
    private final com.tradingsystem.mapper.AccountDataMapper accountDataMapper;

    public CustomerPreferencesService(CustomerPreferencesDataMapper mapper, com.tradingsystem.mapper.AccountDataMapper accountDataMapper) {
        this.mapper = mapper;
        this.accountDataMapper = accountDataMapper;
    }

    public CustomerPreferences getPreferences(Long accountId) {
        CustomerPreferences prefs = mapper.findByAccountId(accountId);
        if (prefs == null) {
            throw new AccountNotFoundException();
        }
        return prefs;
    }

    public CustomerPreferences upsertPreferences(Long accountId, CustomerPreferences payload) {
        Account account = accountDataMapper.findAccountById(accountId);
        if (account == null) {
            throw new AccountNotFoundException();
        }

        // ensure accountId is set on payload
        payload.setAccountId(accountId);

        mapper.upsertCustomerPreferences(payload);
        return mapper.findByAccountId(accountId);
    }

    public List<String> resolveChannels(Long accountId, String eventType) {
        CustomerPreferences prefs = mapper.findByAccountId(accountId);
        if (prefs == null) {
            return List.of("IN_APP");
        }
        return "PRICE_ALERTS".equals(eventType) ? prefs.getNotificationsPriceAlerts() : prefs.getNotificationsTradeFills();
    }
}
