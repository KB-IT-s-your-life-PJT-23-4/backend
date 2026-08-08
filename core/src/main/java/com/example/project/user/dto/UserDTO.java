package com.example.project.user.dto;

import com.example.project.user.domain.UserVO;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.RequiredArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@RequiredArgsConstructor
@ApiModel(description = "비밀번호를 제외한 회원 응답")
public class UserDTO {

    @ApiModelProperty(value = "회원 ID", example = "1")
    private final Long userId;
    @ApiModelProperty(value = "회원 이메일", example = "user@example.com")
    private final String email;
    @ApiModelProperty(value = "회원 이름", example = "홍길동")
    private final String name;
    @ApiModelProperty(value = "생년월일", example = "1990-01-01")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    private final LocalDate birthDate;
    @ApiModelProperty(value = "휴대전화 번호", example = "010-1234-5678")
    private final String phone;
    @ApiModelProperty(value = "회원 권한", example = "USER")
    private final String role;
    @ApiModelProperty(value = "계정 상태", example = "ACTIVE")
    private final String accountStatus;
    @ApiModelProperty(value = "차단 만료 시각")
    private final LocalDateTime blockedUntil;
    @ApiModelProperty(value = "프로필 이미지 경로")
    private final String img;
    @ApiModelProperty(value = "가입 시각")
    private final LocalDateTime createdAt;
    @ApiModelProperty(value = "최근 수정 시각")
    private final LocalDateTime updatedAt;

    public static UserDTO from(UserVO userVO) {
        return new UserDTO(
                userVO.getUserId(),
                userVO.getEmail(),
                userVO.getUserName(),
                userVO.getBirthDate(),
                userVO.getPhone(),
                userVO.getRole(),
                userVO.getAccountStatus(),
                userVO.getBlockedUntil(),
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

    public String accountStatus() {
        return accountStatus;
    }

    public LocalDateTime blockedUntil() {
        return blockedUntil;
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
