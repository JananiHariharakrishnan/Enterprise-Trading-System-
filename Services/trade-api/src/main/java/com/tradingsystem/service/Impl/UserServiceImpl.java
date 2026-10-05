package com.tradingsystem.service.Impl;

import com.tradingsystem.mapper.UserDataMapper;
import com.tradingsystem.model.User;
import com.tradingsystem.service.UserService;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.UUID;

@Service
public class UserServiceImpl implements UserService {

    private final UserDataMapper userDataMapper;

    public UserServiceImpl(UserDataMapper userDataMapper) {
        this.userDataMapper = userDataMapper;
    }

    @Override
    public Long getAvailableAccountId() {
        return userDataMapper.findAvailableAccountId();
    }

    @Override
    public User getUserByUsername(String username) {
        return userDataMapper.findUserByUsername(username);
    }

    @Override
    public User getUserById(UUID id) {
        return userDataMapper.findUserById(id);
    }

    @Override
    public User getUserByAccountId(Long accountId) {
        return userDataMapper.findUserByAccountId(accountId);
    }

    @Override
    public void createUser(String username, Long accountId, String[] roles) {
        // Convert roles array to PostgreSQL array format: {CUSTOMER,ADMIN}
        String rolesJson = "{" + String.join(",", roles) + "}";
        userDataMapper.createUser(username, accountId, rolesJson);
    }

    @Override
    public User updateUserProfile(UUID id, String firstName, String lastName, String email, String phoneNo) {
        userDataMapper.updateUserProfile(id, firstName, lastName, email, phoneNo);
        return userDataMapper.findUserById(id);
    }

    @Override
    public boolean accountExists(Long accountId) {
        return userDataMapper.accountExists(accountId);
    }

    @Override
    public int deleteOrphanCredentials() {
        return userDataMapper.deleteOrphanCredentials();
    }
}
