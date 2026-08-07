package com.example.project.admin.user.mapper;

import com.example.project.admin.user.domain.AdminUserRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface AdminUserMapper {

    long countUsers(
            @Param("userId") Long userId,
            @Param("email") String email,
            @Param("name") String name
    );

    List<AdminUserRecord> selectUsers(
            @Param("userId") Long userId,
            @Param("email") String email,
            @Param("name") String name,
            @Param("offset") long offset,
            @Param("size") int size
    );

    AdminUserRecord selectUserById(@Param("userId") Long userId);
}
