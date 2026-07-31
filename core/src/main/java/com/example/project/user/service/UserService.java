package com.example.project.user.service;

import com.example.project.common.api.ResponseCode;
import com.example.project.common.exception.ServiceException;
import com.example.project.user.domain.UserVO;
import com.example.project.user.dto.UserDTO;
import com.example.project.user.dto.request.UserSignupRequest;
import com.example.project.user.dto.request.UserUpdateRequest;
import com.example.project.user.dto.response.EmailAvailabilityResponse;
import com.example.project.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Locale;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    public UserDTO signup(UserSignupRequest signupRequest) {
        String normalizedEmail = normalizeEmail(signupRequest.email());

        if (userMapper.findByEmail(normalizedEmail) != null) {
            throw new ServiceException(ResponseCode.DUPLICATE_DATA);
        }

        UserVO newUser = new UserVO(
                null,
                normalizedEmail,
                passwordEncoder.encode(signupRequest.password()),
                signupRequest.name().trim(),
                signupRequest.birthDate(),
                signupRequest.phone().trim(),
                null,
                null,
                null,
                normalizeNullable(signupRequest.img())
        );

        try {
            if (userMapper.insert(newUser) != 1) {
                throw new ServiceException(ResponseCode.DATABASE_ERROR);
            }
        } catch (DuplicateKeyException exception) {
            throw new ServiceException(ResponseCode.DUPLICATE_DATA);
        }

        UserVO savedUser = userMapper.findById(newUser.getUserId());
        if (savedUser == null) {
            throw new ServiceException(ResponseCode.DATABASE_ERROR);
        }

        return UserDTO.from(savedUser);
    }

    public EmailAvailabilityResponse checkEmailAvailability(String email) {
        String normalizedEmail = normalizeEmail(email);
        return new EmailAvailabilityResponse(userMapper.findByEmail(normalizedEmail) == null);
    }

    public UserDTO getProfile(Long userId) {
        return UserDTO.from(findUser(userId));
    }

    public UserDTO updateProfile(Long userId, UserUpdateRequest updateRequest) {
        UserVO existingUser = findUser(userId);
        String normalizedEmail = normalizeEmail(updateRequest.email());
        UserVO userWithSameEmail = userMapper.findByEmail(normalizedEmail);

        if (userWithSameEmail != null && !userWithSameEmail.getUserId().equals(userId)) {
            throw new ServiceException(ResponseCode.DUPLICATE_DATA);
        }

        existingUser.setEmail(normalizedEmail);
        existingUser.setUserName(updateRequest.name().trim());
        existingUser.setBirthDate(updateRequest.birthDate());
        existingUser.setPhone(updateRequest.phone().trim());
        existingUser.setImg(normalizeNullable(updateRequest.img()));

        try {
            if (userMapper.update(existingUser) != 1) {
                throw new ServiceException(ResponseCode.DATABASE_ERROR);
            }
        } catch (DuplicateKeyException exception) {
            throw new ServiceException(ResponseCode.DUPLICATE_DATA);
        }

        UserVO updatedUser = userMapper.findById(userId);
        if (updatedUser == null) {
            throw new ServiceException(ResponseCode.DATABASE_ERROR);
        }

        return UserDTO.from(updatedUser);
    }

    public void deleteUser(Long userId) {
        findUser(userId);

        if (userMapper.deleteById(userId) != 1) {
            throw new ServiceException(ResponseCode.DATABASE_ERROR);
        }
    }

    private UserVO findUser(Long userId) {
        UserVO foundUser = userMapper.findById(userId);
        if (foundUser == null) {
            throw new ServiceException(ResponseCode.MEMBER_NOT_FOUND);
        }

        return foundUser;
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeNullable(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        return value.trim();
    }
}
