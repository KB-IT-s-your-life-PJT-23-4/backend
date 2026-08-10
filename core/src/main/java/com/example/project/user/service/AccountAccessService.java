package com.example.project.user.service;

import com.example.project.common.api.ResponseCode;
import com.example.project.common.exception.ServiceException;
import com.example.project.user.domain.UserVO;
import com.example.project.user.mapper.AccountStatusMapper;
import com.example.project.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AccountAccessService {

    private static final String BLOCKED = "BLOCKED";

    private final AccountStatusMapper accountStatusMapper;
    private final UserMapper userMapper;
    private final Clock clock;

    @Transactional
    public UserVO refreshAndGet(Long userId) {
        if (userId == null || userId <= 0L) {
            throw new ServiceException(ResponseCode.BAD_REQUEST);
        }

        accountStatusMapper.activateExpiredBlock(userId);
        UserVO user = userMapper.findById(userId);
        if (user == null) {
            throw new ServiceException(ResponseCode.MEMBER_NOT_FOUND);
        }
        return user;
    }

    @Transactional
    public int refreshAllExpiredBlocks() {
        return accountStatusMapper.activateAllExpiredBlocks();
    }

    @Transactional
    public void requireRestrictedFeatureAccess(Long userId) {
        UserVO user = refreshAndGet(userId);
        if (BLOCKED.equals(user.getAccountStatus())
                && user.getBlockedUntil() != null
                && user.getBlockedUntil().isAfter(LocalDateTime.now(clock))) {
            throw new ServiceException(ResponseCode.FORBIDDEN);
        }
    }
}
