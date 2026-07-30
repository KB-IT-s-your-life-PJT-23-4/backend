package com.example.project.user.dto;

import com.example.project.user.domain.UserVO;
import lombok.Data;
import lombok.RequiredArgsConstructor;

import java.time.LocalDateTime;

@Data
@RequiredArgsConstructor
public class UserDTO {

    private final Long userId;
    private final String email;
    private final String name;
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;

    public static UserDTO from(UserVO userVO) {
        return new UserDTO(
                userVO.getUserId(),
                userVO.getEmail(),
                userVO.getUserName(),
                userVO.getCreatedAt(),
                userVO.getUpdatedAt()
        );
    }

    public Long userId() {
        return userId;
    }

    public String email() {
        return email;
    }

    public String name() {
        return name;
    }

    public LocalDateTime createdAt() {
        return createdAt;
    }

    public LocalDateTime updatedAt() {
        return updatedAt;
    }
}
