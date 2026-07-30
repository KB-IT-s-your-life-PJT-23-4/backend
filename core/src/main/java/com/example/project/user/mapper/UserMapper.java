package com.example.project.user.mapper;

import com.example.project.user.domain.UserVO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserMapper {

    UserVO findById(Long userId);

    UserVO findByEmail(String email);

    int insert(UserVO userVO);

    int update(UserVO userVO);

    int deleteById(Long userId);
}
