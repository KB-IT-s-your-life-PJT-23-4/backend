package com.example.project.user.integration;

import com.example.project.config.RootConfig;
import com.example.project.consultation.config.WebClientConfig;
import com.example.project.security.SecurityConfig;
import com.example.project.user.domain.UserVO;
import com.example.project.user.dto.UserDTO;
import com.example.project.user.dto.request.UserSignupRequest;
import com.example.project.user.dto.request.UserUpdateRequest;
import com.example.project.user.mapper.UserMapper;
import com.example.project.user.service.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = {RootConfig.class, SecurityConfig.class, WebClientConfig.class})
@Transactional
class UserDatabaseIntegrationTest {

    @Autowired
    private UserService userService;

    @Autowired
    private UserMapper userMapper;

    @Test
    @DisplayName("회원가입, 조회, 수정 시 최종 DDL의 User 컬럼이 MyBatis로 연동된다")
    void userColumnsAreMappedThroughSignupReadAndUpdate() {
        long suffix = Math.floorMod(System.nanoTime(), 100_000_000L);
        String email = "user-db-" + suffix + "@example.com";
        String phone = String.format("010%08d", suffix);

        UserDTO created = userService.signup(new UserSignupRequest(
                email,
                "password123!",
                "테스트회원",
                LocalDate.of(1990, 1, 1),
                phone,
                "profile.png"
        ));

        assertNotNull(created.userId());
        assertEquals(email, created.email());
        assertEquals(LocalDate.of(1990, 1, 1), created.birthDate());
        assertEquals(phone, created.phone());
        assertEquals("USER", created.role());
        assertEquals("profile.png", created.img());

        UserVO foundById = userMapper.findById(created.userId());
        UserVO foundByEmail = userMapper.findByEmail(email);

        assertNotNull(foundById);
        assertNotNull(foundByEmail);
        assertEquals(created.userId(), foundByEmail.getUserId());
        assertNotEquals("password123!", foundById.getPassword());
        assertNotNull(foundById.getCreatedAt());
        assertNotNull(foundById.getUpdatedAt());

        String updatedEmail = "updated-db-" + suffix + "@example.com";
        String updatedPhone = String.format("011%08d", suffix);
        UserDTO updated = userService.updateProfile(
                created.userId(),
                new UserUpdateRequest(
                        updatedEmail,
                        "수정회원",
                        LocalDate.of(1991, 2, 2),
                        updatedPhone,
                        "updated.png"
                )
        );

        assertEquals(updatedEmail, updated.email());
        assertEquals("수정회원", updated.name());
        assertEquals(LocalDate.of(1991, 2, 2), updated.birthDate());
        assertEquals(updatedPhone, updated.phone());
        assertEquals("USER", updated.role());
        assertEquals("updated.png", updated.img());

        UserVO reloaded = userMapper.findById(created.userId());
        assertEquals(updatedEmail, reloaded.getEmail());
        assertEquals(updatedPhone, reloaded.getPhone());
        assertEquals("updated.png", reloaded.getImg());
    }
}
