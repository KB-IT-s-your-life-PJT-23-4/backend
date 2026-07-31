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

class UserUpdateRequestTest {

    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    @Test
    @DisplayName("올바른 회원 정보 수정 입력값은 검증을 통과한다")
    void validRequest() {
        UserUpdateRequest request = new UserUpdateRequest(
                "user@example.com",
                "홍길동",
                LocalDate.of(1990, 1, 1),
                "010-1234-5678",
                "profile.png"
        );

        Set<ConstraintViolation<UserUpdateRequest>> violations = validator.validate(request);

        assertTrue(violations.isEmpty());
    }

    @Test
    @DisplayName("잘못된 이메일과 빈 이름은 검증에 실패한다")
    void invalidRequest() {
        UserUpdateRequest request = new UserUpdateRequest(
                "invalid-email",
                " ",
                null,
                " ",
                null
        );

        Set<ConstraintViolation<UserUpdateRequest>> violations = validator.validate(request);

        Set<String> invalidFields = violations.stream()
                .map(violation -> violation.getPropertyPath().toString())
                .collect(Collectors.toSet());

        assertEquals(Set.of("email", "name", "phone"), invalidFields);
    }
}
