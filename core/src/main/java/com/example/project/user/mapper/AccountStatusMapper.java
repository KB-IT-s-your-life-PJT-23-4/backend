package com.example.project.user.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AccountStatusMapper {

    int activateExpiredBlock(@Param("userId") Long userId);
}
