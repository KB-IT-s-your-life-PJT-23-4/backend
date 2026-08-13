package com.example.project.user.service;

import com.example.project.common.file.ProfileImageStorageService;
import com.example.project.user.domain.UserVO;
import com.example.project.user.mapper.UserMapper;
import com.example.project.user.mapper.UserWithdrawalMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UserServiceTransactionTest {

    @TempDir
    Path tempDirectory;

    @Test
    @DisplayName("회원 파기 후 같은 트랜잭션이 실패하면 DB 작업을 모두 롤백하고 이미지를 보존한다")
    void rollbackEveryDatabaseDeletionAndKeepImage() {
        ProfileImageStorageService storage = new ProfileImageStorageService(tempDirectory.toString(), 1024);
        String imagePath = storage.store(new MockMultipartFile(
                "image",
                "profile.png",
                "image/png",
                new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0d, 0x0a, 0x1a, 0x0a, 0, 0, 0, 0}
        ));
        TransactionalUserMapper mapper = new TransactionalUserMapper(user(imagePath));
        SnapshotTransactionManager transactionManager = new SnapshotTransactionManager(mapper);
        UserService userService = new UserService(
                mapper, new BCryptPasswordEncoder(), storage, null, mapper,
                com.example.project.support.PiiTestSupport.protectionService()
        );
        WithdrawalFacade facade = transactionalProxy(
                new WithdrawalFacade(userService),
                transactionManager,
                WithdrawalFacade.class
        );

        assertThrows(RuntimeException.class, () -> facade.withdrawAndFail(1L));

        assertTrue(mapper.userExists);
        assertEquals(1, mapper.reportsByEvent);
        assertEquals(1, mapper.reportsByUser);
        assertEquals(1, mapper.events);
        assertEquals(1, mapper.conversations);
        assertEquals(1, mapper.tickets);
        assertTrue(Files.exists(managedFile(imagePath)));
    }

    private <T> T transactionalProxy(
            T target,
            SnapshotTransactionManager transactionManager,
            Class<T> targetType
    ) {
        TransactionInterceptor interceptor = new TransactionInterceptor();
        interceptor.setTransactionManager(transactionManager);
        interceptor.setTransactionAttributeSource(new AnnotationTransactionAttributeSource());
        ProxyFactory proxyFactory = new ProxyFactory(target);
        proxyFactory.setProxyTargetClass(true);
        proxyFactory.addAdvice(interceptor);
        return targetType.cast(proxyFactory.getProxy());
    }

    private Path managedFile(String publicPath) {
        return tempDirectory.resolve("profile-images").resolve(
                publicPath.substring(ProfileImageStorageService.PUBLIC_PATH_PREFIX.length())
        );
    }

    private UserVO user(String imagePath) {
        return new UserVO(
                1L,
                "withdrawal@example.com",
                "encoded-password",
                "가상회원",
                LocalDate.of(1990, 1, 1),
                "010-0000-0000",
                "USER",
                LocalDateTime.of(2026, 8, 11, 10, 0),
                LocalDateTime.of(2026, 8, 11, 10, 0),
                imagePath
        );
    }

    static class WithdrawalFacade {

        private final UserService userService;

        WithdrawalFacade(UserService userService) {
            this.userService = userService;
        }

        @Transactional
        public void withdrawAndFail(Long userId) {
            userService.deleteUser(userId);
            throw new RuntimeException("downstream transaction failure");
        }
    }

    private static class SnapshotTransactionManager extends AbstractPlatformTransactionManager {

        private final TransactionalUserMapper mapper;

        private SnapshotTransactionManager(TransactionalUserMapper mapper) {
            this.mapper = mapper;
        }

        @Override
        protected Object doGetTransaction() {
            return new Object();
        }

        @Override
        protected void doBegin(Object transaction, TransactionDefinition definition) {
            mapper.takeSnapshot();
        }

        @Override
        protected void doCommit(DefaultTransactionStatus status) {
        }

        @Override
        protected void doRollback(DefaultTransactionStatus status) {
            mapper.restoreSnapshot();
        }
    }

    private static class TransactionalUserMapper implements UserMapper, UserWithdrawalMapper {

        private final UserVO user;
        private boolean userExists = true;
        private int reportsByEvent = 1;
        private int reportsByUser = 1;
        private int events = 1;
        private int conversations = 1;
        private int tickets = 1;
        private Snapshot snapshot;

        private TransactionalUserMapper(UserVO user) {
            this.user = user;
        }

        private void takeSnapshot() {
            snapshot = new Snapshot(
                    userExists,
                    reportsByEvent,
                    reportsByUser,
                    events,
                    conversations,
                    tickets
            );
        }

        private void restoreSnapshot() {
            userExists = snapshot.userExists;
            reportsByEvent = snapshot.reportsByEvent;
            reportsByUser = snapshot.reportsByUser;
            events = snapshot.events;
            conversations = snapshot.conversations;
            tickets = snapshot.tickets;
        }

        @Override
        public UserVO findById(Long userId) {
            return userExists && user.getUserId().equals(userId) ? user : null;
        }

        @Override
        public UserVO findByEmail(String email) {
            return null;
        }

        @Override
        public int insert(UserVO userVO) {
            return 0;
        }

        @Override
        public int update(UserVO userVO) {
            return 0;
        }

        @Override
        public List<String> findFamilyImagePaths(Long userId) {
            return List.of();
        }

        @Override
        public int deleteAiSafetyReportsByTriggerEventUserId(Long userId) {
            int deleted = reportsByEvent;
            reportsByEvent = 0;
            return deleted;
        }

        @Override
        public int deleteAiSafetyReportsByUserId(Long userId) {
            int deleted = reportsByUser;
            reportsByUser = 0;
            return deleted;
        }

        @Override
        public int deleteAiConsultationEventsByUserId(Long userId) {
            int deleted = events;
            events = 0;
            return deleted;
        }

        @Override
        public int deleteAiConversationsByUserId(Long userId) {
            int deleted = conversations;
            conversations = 0;
            return deleted;
        }

        @Override
        public int deleteTicketsByUserId(Long userId) {
            int deleted = tickets;
            tickets = 0;
            return deleted;
        }

        @Override
        public int deleteById(Long userId) {
            if (!userExists || !user.getUserId().equals(userId)) {
                return 0;
            }
            userExists = false;
            return 1;
        }
    }

    private static class Snapshot {

        private final boolean userExists;
        private final int reportsByEvent;
        private final int reportsByUser;
        private final int events;
        private final int conversations;
        private final int tickets;

        private Snapshot(
                boolean userExists,
                int reportsByEvent,
                int reportsByUser,
                int events,
                int conversations,
                int tickets
        ) {
            this.userExists = userExists;
            this.reportsByEvent = reportsByEvent;
            this.reportsByUser = reportsByUser;
            this.events = events;
            this.conversations = conversations;
            this.tickets = tickets;
        }
    }
}
