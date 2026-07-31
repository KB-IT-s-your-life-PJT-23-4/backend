package com.example.project.user.dto.request;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.validation.ConstraintViolation;
import javax.validation.Validation;
import javax.validation.Validator;
import java.time.LocalDate;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UserSignupRequestTest {

    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    @Test
    @DisplayName("올바른 회원가입 입력값은 검증을 통과한다")
    void validRequest() {
        UserSignupRequest request = new UserSignupRequest(
                "user@example.com",
                "password123!",
                "홍길동",
                LocalDate.of(1990, 1, 1),
                "010-1234-5678",
                "profile.png"
        );

        Set<ConstraintViolation<UserSignupRequest>> violations = validator.validate(request);

        assertTrue(violations.isEmpty());
    }

    @Test
    @DisplayName("잘못된 이메일과 짧은 비밀번호 및 빈 이름은 검증에 실패한다")
    void invalidRequest() {
        UserSignupRequest request = new UserSignupRequest(
                "invalid-email",
                "short",
                " ",
                null,
                " ",
                null
        );

        Set<ConstraintViolation<UserSignupRequest>> violations = validator.validate(request);

        Set<String> invalidFields = violations.stream()
                .map(violation -> violation.getPropertyPath().toString())
                .collect(Collectors.toSet());

        assertEquals(Set.of("email", "password", "name", "phone"), invalidFields);
    }

    @Test
    @DisplayName("앞뒤에 공백이 있는 이름은 검증에 실패한다")
    void nameWithLeadingOrTrailingWhitespace() {
        UserSignupRequest request = new UserSignupRequest(
                "user@example.com",
                "password123!",
                " 홍 ",
                null,
                "010-1234-5678",
                null
        );

        Set<ConstraintViolation<UserSignupRequest>> violations = validator.validate(request);

        assertTrue(violations.stream()
                .anyMatch(violation -> violation.getPropertyPath().toString().equals("name")));
    }
}
