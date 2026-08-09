package com.example.project.admin.auth.mapper;

import com.example.project.user.domain.UserVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;
import java.util.Set;

@Mapper
public interface AdminAuthMapper {

    List<UserVO> getAdmins(
            @Param("roles") Set<String> roles,
            @Param("offset") long offset,
            @Param("size") int size
    );

    long getAdminCounts(@Param("roles") Set<String> roles);

    Optional<UserVO> findByUserId(@Param("userId") long userId);

    int changeAuth(
            @Param("userId") Long userId,
            @Param("role") String role
    );

    int deleteAuth(@Param("userId") Long userId);
}
