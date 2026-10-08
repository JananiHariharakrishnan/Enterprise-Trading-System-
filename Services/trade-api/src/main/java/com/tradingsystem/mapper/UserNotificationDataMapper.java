package com.tradingsystem.mapper;

import com.tradingsystem.model.UserNotification;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface UserNotificationDataMapper {

    int insert(UserNotification notification);

    List<UserNotification> findByAccountId(@Param("accountId") Long accountId);

    int markAllRead(@Param("accountId") Long accountId);
}
