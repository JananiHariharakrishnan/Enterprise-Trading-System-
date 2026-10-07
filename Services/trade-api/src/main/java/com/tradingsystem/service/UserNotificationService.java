package com.tradingsystem.service;

import com.tradingsystem.mapper.UserNotificationDataMapper;
import com.tradingsystem.model.UserNotification;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class UserNotificationService {

    private final UserNotificationDataMapper mapper;

    public UserNotificationService(UserNotificationDataMapper mapper) {
        this.mapper = mapper;
    }

    public List<UserNotification> listForAccount(Long accountId) {
        return mapper.findByAccountId(accountId);
    }

    public void markAllRead(Long accountId) {
        mapper.markAllRead(accountId);
    }
}
