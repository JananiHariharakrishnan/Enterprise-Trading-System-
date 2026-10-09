package com.tradingsystem.service.Impl;

import com.tradingsystem.mapper.UserDataMapper;
import com.tradingsystem.model.Account;
import com.tradingsystem.model.User;
import com.tradingsystem.service.AdminService;
import com.tradingsystem.service.UserService;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class UserServiceImpl implements UserService {

    private final UserDataMapper userDataMapper;
    private final AdminService adminService;

    public UserServiceImpl(UserDataMapper userDataMapper, AdminService adminService) {
        this.userDataMapper = userDataMapper;
        this.adminService = adminService;
    }

    @Override
    public Long getAvailableAccountId() {
        Long existing = userDataMapper.findAvailableAccountId();
        if (existing != null) {
            return existing;
        }
        Account created = adminService.createUnmappedAccount(null);
        return created.getAccountId();
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
