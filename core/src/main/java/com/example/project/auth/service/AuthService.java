package com.example.project.auth.service;

import com.example.project.auth.dto.request.LoginRequest;
import com.example.project.auth.dto.request.LogoutRequest;
import com.example.project.auth.dto.request.TokenRefreshRequest;
import com.example.project.auth.dto.response.AuthTokenResponse;
import com.example.project.common.api.ResponseCode;
import com.example.project.common.exception.ServiceException;
import com.example.project.security.JwtProvider;
import com.example.project.security.TokenRevocationStore;
import com.example.project.user.domain.UserVO;
import com.example.project.user.dto.UserDTO;
import com.example.project.user.mapper.UserMapper;
import com.example.project.user.crypto.UserPiiProtectionService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Locale;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;
    private final TokenRevocationStore tokenRevocationStore;
    private final UserPiiProtectionService piiProtectionService;

    public AuthTokenResponse login(LoginRequest request) {
        String email = normalizeEmail(request.email());
        UserVO user = piiProtectionService.reveal(
                userMapper.findByEmail(piiProtectionService.emailLookup(email))
        );

        if (user == null || !passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new ServiceException(ResponseCode.UNAUTHORIZED);
        }

        return issueTokens(user);
    }

    public AuthTokenResponse refresh(TokenRefreshRequest request) {
        String refreshToken = request.refreshToken();
        validateRefreshToken(refreshToken);

        UserVO user = findUserByToken(refreshToken);
        AuthTokenResponse response = issueTokens(user);

        tokenRevocationStore.revoke(refreshToken, jwtProvider.getExpiration(refreshToken));
        return response;
    }

    public void logout(LogoutRequest request, String accessToken) {
        String refreshToken = request.refreshToken();

        if (tokenRevocationStore.isRevoked(refreshToken) || jwtProvider.isExpired(refreshToken)) {
            return;
        }

        if (!jwtProvider.isValidRefreshToken(refreshToken)) {
            throw new ServiceException(ResponseCode.UNAUTHORIZED);
        }

        String userId = jwtProvider.getSubject(refreshToken);
        revokeAccessToken(accessToken, userId);
        tokenRevocationStore.revoke(refreshToken, jwtProvider.getExpiration(refreshToken));
    }

    private AuthTokenResponse issueTokens(UserVO user) {
        String subject = String.valueOf(user.getUserId());
        String accessToken = jwtProvider.createAccessToken(subject, user.getRole());
        String refreshToken = jwtProvider.createRefreshToken(subject);

        return new AuthTokenResponse(accessToken, refreshToken, UserDTO.from(user));
    }

    private void validateRefreshToken(String refreshToken) {
        if (tokenRevocationStore.isRevoked(refreshToken)) {
            throw new ServiceException(ResponseCode.UNAUTHORIZED);
        }

        if (jwtProvider.isExpired(refreshToken)) {
            throw new ServiceException(ResponseCode.TOKEN_EXPIRED);
        }

        if (!jwtProvider.isValidRefreshToken(refreshToken)) {
            throw new ServiceException(ResponseCode.UNAUTHORIZED);
        }
    }

    private UserVO findUserByToken(String token) {
        Long userId;

        try {
            userId = Long.valueOf(jwtProvider.getSubject(token));
        } catch (NumberFormatException e) {
            throw new ServiceException(ResponseCode.UNAUTHORIZED);
        }

        UserVO user = piiProtectionService.reveal(userMapper.findById(userId));
        if (user == null) {
            throw new ServiceException(ResponseCode.MEMBER_NOT_FOUND);
        }

        return user;
    }

    private void revokeAccessToken(String accessToken, String userId) {
        if (accessToken == null) {
            throw new ServiceException(ResponseCode.UNAUTHORIZED);
        }

        if (jwtProvider.isExpired(accessToken)) {
            return;
        }

        if (!jwtProvider.isValidAccessToken(accessToken)
                || !userId.equals(jwtProvider.getSubject(accessToken))) {
            throw new ServiceException(ResponseCode.UNAUTHORIZED);
        }

        tokenRevocationStore.revoke(accessToken, jwtProvider.getExpiration(accessToken));
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
