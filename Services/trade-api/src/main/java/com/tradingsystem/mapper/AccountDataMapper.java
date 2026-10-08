package com.tradingsystem.mapper;

import com.tradingsystem.dto.response.AdminDashboardStats;
import com.tradingsystem.dto.response.UnassignedAccountSummary;
import com.tradingsystem.model.Account;
import com.tradingsystem.model.Position;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Mapper
public interface AccountDataMapper {

    Account findAccountById(@Param("accountId") Long accountId);

    Account findAccountByReference(@Param("accountReference") String accountReference);

    String findAccountHolderName(@Param("accountId") Long accountId);

    List<Position> findPositionsByAccountId(@Param("accountId") Long accountId);

    AdminDashboardStats getDashboardAccountStats();

    long countTotalUsers();

    long countUnmappedAccounts();

    List<UnassignedAccountSummary> findUnassignedAndDormantAccounts();

    int updateAccountStatus(@Param("accountId") Long accountId, @Param("status") String status);

    int updateAccountStatusByUserId(@Param("userId") UUID userId, @Param("status") String status);

    int insertAccount(
            @Param("accountReference") String accountReference,
            @Param("cashBalance") BigDecimal cashBalance,
            @Param("buyingPower") BigDecimal buyingPower,
            @Param("status") String status
    );

    int depositFunds(@Param("accountId") Long accountId, @Param("amount") BigDecimal amount);

    int insertBalanceHistory(
            @Param("accountId") Long accountId,
            @Param("type") String type,
            @Param("amount") BigDecimal amount
    );

    /**
     * Find the maximum account reference in the format ETP-220XX
     * Returns null if no accounts exist
     */
    String findMaxAccountReference();
}

