package com.tradingsystem.mapper;

import com.tradingsystem.model.CustomerPreferences;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface CustomerPreferencesDataMapper {

    CustomerPreferences findByAccountId(@Param("accountId") Long accountId);

    int upsertCustomerPreferences(CustomerPreferences prefs);

}
