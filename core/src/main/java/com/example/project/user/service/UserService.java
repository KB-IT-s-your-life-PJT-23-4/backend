package com.example.project.user.service;

import com.example.project.common.api.ResponseCode;
import com.example.project.common.exception.ServiceException;
import com.example.project.user.domain.UserVO;
import com.example.project.user.dto.UserDTO;
import com.example.project.user.dto.request.UserSignupRequest;
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

    public UserDTO signup(UserSignupRequest request) {
        String email = normalizeEmail(request.email());

        if (userMapper.findByEmail(email) != null) {
            throw new ServiceException(ResponseCode.DUPLICATE_DATA);
        }

        UserVO user = new UserVO(
                null,
                email,
                passwordEncoder.encode(request.password()),
                request.name().trim(),
                null,
                null
        );

        try {
            if (userMapper.insert(user) != 1) {
                throw new ServiceException(ResponseCode.DATABASE_ERROR);
            }
        } catch (DuplicateKeyException e) {
            throw new ServiceException(ResponseCode.DUPLICATE_DATA);
        }

        UserVO savedUser = userMapper.findById(user.getUserId());
        if (savedUser == null) {
            throw new ServiceException(ResponseCode.DATABASE_ERROR);
        }

        return UserDTO.from(savedUser);
    }

    public EmailAvailabilityResponse checkEmailAvailability(String email) {
        String normalizedEmail = normalizeEmail(email);
        return new EmailAvailabilityResponse(userMapper.findByEmail(normalizedEmail) == null);
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
