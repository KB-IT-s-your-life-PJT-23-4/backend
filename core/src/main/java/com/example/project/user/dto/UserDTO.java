package com.example.project.user.dto;

import com.example.project.user.domain.UserVO;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import lombok.RequiredArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@RequiredArgsConstructor
public class UserDTO {

    private final Long userId;
    private final String email;
    private final String name;
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    private final LocalDate birthDate;
    private final String phone;
    private final String role;
    private final String img;
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;

    public static UserDTO from(UserVO userVO) {
        return new UserDTO(
                userVO.getUserId(),
                userVO.getEmail(),
                userVO.getUserName(),
                userVO.getBirthDate(),
                userVO.getPhone(),
                userVO.getRole(),
                userVO.getImg(),
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

    public LocalDate birthDate() {
        return birthDate;
    }

    public String phone() {
        return phone;
    }

    public String role() {
        return role;
    }

    public String img() {
        return img;
    }

    public LocalDateTime createdAt() {
        return createdAt;
    }

    public LocalDateTime updatedAt() {
        return updatedAt;
    }
}
