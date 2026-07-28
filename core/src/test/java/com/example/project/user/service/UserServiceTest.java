package com.example.project.user.service;

import com.example.project.common.api.ResponseCode;
import com.example.project.common.exception.ServiceException;
import com.example.project.user.domain.UserVO;
import com.example.project.user.dto.UserDTO;
import com.example.project.user.dto.request.UserSignupRequest;
import com.example.project.user.mapper.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UserServiceTest {

    private FakeUserMapper userMapper;
    private PasswordEncoder passwordEncoder;
    private UserService userService;

    @BeforeEach
    void setUp() {
        userMapper = new FakeUserMapper();
        passwordEncoder = new BCryptPasswordEncoder();
        userService = new UserService(userMapper, passwordEncoder);
    }

    @Test
    @DisplayName("회원가입 시 이메일을 정규화하고 비밀번호를 암호화한다")
    void signup() {
        UserSignupRequest request = new UserSignupRequest(
                " User@Example.com ",
                "password123!",
                " 홍길동 "
        );

        UserDTO result = userService.signup(request);

        assertEquals(1L, result.userId());
        assertEquals("user@example.com", result.email());
        assertEquals("홍길동", result.name());
        assertNotEquals(request.password(), userMapper.savedUser.getPassword());
        assertTrue(passwordEncoder.matches(request.password(), userMapper.savedUser.getPassword()));
    }

    @Test
    @DisplayName("이미 가입된 이메일이면 회원가입에 실패한다")
    void signupWithDuplicateEmail() {
        userMapper.savedUser = createUser("user@example.com");
        UserSignupRequest request = new UserSignupRequest(
                "USER@example.com",
                "password123!",
                "홍길동"
        );

        ServiceException exception = assertThrows(
                ServiceException.class,
                () -> userService.signup(request)
        );

        assertEquals(ResponseCode.DUPLICATE_DATA, exception.getResponseCode());
        assertEquals(0, userMapper.insertCount);
    }

    @Test
    @DisplayName("이메일 사용 가능 여부를 반환한다")
    void checkEmailAvailability() {
        assertTrue(userService.checkEmailAvailability("new@example.com").available());

        userMapper.savedUser = createUser("user@example.com");

        assertFalse(userService.checkEmailAvailability("USER@example.com").available());
    }

    private UserVO createUser(String email) {
        return new UserVO(
                1L,
                email,
                passwordEncoder.encode("password123!"),
                "홍길동",
                LocalDateTime.now(),
                LocalDateTime.now()
        );
    }

    private static class FakeUserMapper implements UserMapper {

        private UserVO savedUser;
        private int insertCount;

        @Override
        public UserVO findById(Long userId) {
            if (savedUser == null || !savedUser.getUserId().equals(userId)) {
                return null;
            }
            return savedUser;
        }

        @Override
        public UserVO findByEmail(String email) {
            if (savedUser == null || !savedUser.getEmail().equals(email)) {
                return null;
            }
            return savedUser;
        }

        @Override
        public int insert(UserVO user) {
            insertCount++;
            user.setUserId(1L);
            user.setCreatedAt(LocalDateTime.now());
            user.setUpdatedAt(LocalDateTime.now());
            savedUser = user;
            return 1;
        }

        @Override
        public int update(UserVO user) {
            return 0;
        }

        @Override
        public int deleteById(Long userId) {
            return 0;
        }
    }
}
