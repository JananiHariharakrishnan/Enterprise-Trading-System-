package com.tradingsystem.mapper;

import com.tradingsystem.model.Account;
import com.tradingsystem.model.Position;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface AccountDataMapper {

    Account findAccountById(@Param("accountId") Long accountId);

    String findAccountHolderName(@Param("accountId") Long accountId);

    List<Position> findPositionsByAccountId(@Param("accountId") Long accountId);
}

