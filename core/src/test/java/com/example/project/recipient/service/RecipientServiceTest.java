package com.example.project.recipient.service;

import com.example.project.common.api.ResponseCode;
import com.example.project.common.exception.ServiceException;
import com.example.project.common.file.ProfileImageStorageService;
import com.example.project.gift.domain.DeductionVO;
import com.example.project.gift.domain.GiftVO;
import com.example.project.gift.domain.Status;
import com.example.project.gift.domain.TaxBracketVO;
import com.example.project.gift.mapper.GiftMapper;
import com.example.project.recipient.domain.RecipientVO;
import com.example.project.recipient.dto.request.RecipientRequest;
import com.example.project.recipient.dto.request.RecipientProfileUpdateRequest;
import com.example.project.recipient.dto.response.RecipientResponse;
import com.example.project.recipient.mapper.RecipientMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecipientServiceTest {

    @TempDir
    Path tempDirectory;

    private static final Long OWNER_ID = 1L;
    private static final Long OTHER_USER_ID = 2L;

    private FakeRecipientMapper recipientMapper;
    private FakeGiftMapper giftMapper;
    private RecipientService recipientService;

    @BeforeEach
    void setUp() {
        recipientMapper = new FakeRecipientMapper();
        giftMapper = new FakeGiftMapper();
        recipientService = new RecipientService(recipientMapper, giftMapper);
    }

    @Test
    @DisplayName("가족을 등록하면 로그인 사용자의 가족으로 저장하고 상세 정보를 반환한다")
    void createRecipient() {
        RecipientRequest request = request("홍길동", "LINEAL_DESCENDANT", LocalDate.of(2010, 1, 2));

        RecipientResponse response = recipientService.createRecipient(request, OWNER_ID);

        assertNotNull(response.getFamilyId());
        assertEquals("홍길동", response.getFamilyName());
        assertEquals("LINEAL_DESCENDANT", response.getRelation());
        assertEquals(OWNER_ID, recipientMapper.findStored(response.getFamilyId()).getUserId());
        assertEquals(1, recipientMapper.insertCount);
    }

    @Test
    @DisplayName("가족 목록은 로그인 사용자가 소유한 데이터만 반환한다")
    void selectAllRecipientsOwnedByCurrentUser() {
        recipientMapper.add(recipient(10L, OWNER_ID, "본인가족"));
        recipientMapper.add(recipient(20L, OTHER_USER_ID, "타인가족"));

        List<RecipientResponse> responses = recipientService.selectAllRecipient(OWNER_ID);

        assertEquals(1, responses.size());
        assertEquals(10L, responses.get(0).getFamilyId());
        assertEquals("본인가족", responses.get(0).getFamilyName());
    }

    @Test
    @DisplayName("가족 상세 조회는 본인이 소유한 데이터를 반환한다")
    void selectOwnedRecipient() {
        recipientMapper.add(recipient(10L, OWNER_ID, "본인가족"));

        RecipientResponse response = recipientService.selectRecipient(10L, OWNER_ID);

        assertEquals(10L, response.getFamilyId());
        assertEquals("본인가족", response.getFamilyName());
    }

    @Test
    @DisplayName("수증자 프로필 수정은 소유권과 관계를 유지하며 사진을 등록·삭제한다")
    void updateEditableRecipientProfile() {
        recipientMapper.add(recipient(10L, OWNER_ID, "기존이름"));
        ProfileImageStorageService storage = new ProfileImageStorageService(tempDirectory.toString(), 1024);
        recipientService = new RecipientService(recipientMapper, giftMapper, storage);
        RecipientProfileUpdateRequest request = new RecipientProfileUpdateRequest(
                "변경이름",
                LocalDate.of(2011, 2, 3)
        );
        MockMultipartFile image = new MockMultipartFile(
                "image",
                "family.png",
                "image/png",
                new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0d, 0x0a, 0x1a, 0x0a, 0, 0, 0, 0}
        );

        RecipientResponse updated = recipientService.updateEditableProfile(
                10L, request, image, false, OWNER_ID
        );
        RecipientResponse removed = recipientService.updateEditableProfile(
                10L, request, null, true, OWNER_ID
        );

        assertEquals("LINEAL_DESCENDANT", updated.getRelation());
        assertTrue(updated.getFamilyImg().startsWith(ProfileImageStorageService.PUBLIC_PATH_PREFIX));
        assertNull(removed.getFamilyImg());
    }

    @Test
    @DisplayName("존재하지 않는 가족을 조회하면 BENEFICIARY_NOT_FOUND가 발생한다")
    void selectMissingRecipient() {
        ServiceException exception = assertThrows(
                ServiceException.class,
                () -> recipientService.selectRecipient(999L, OWNER_ID)
        );

        assertEquals(ResponseCode.BENEFICIARY_NOT_FOUND, exception.getResponseCode());
    }

    @Test
    @DisplayName("타 사용자가 소유한 가족을 조회할 수 없다")
    void rejectOtherUsersRecipientRead() {
        recipientMapper.add(recipient(10L, OTHER_USER_ID, "타인가족"));

        ServiceException exception = assertThrows(
                ServiceException.class,
                () -> recipientService.selectRecipient(10L, OWNER_ID)
        );

        assertEquals(ResponseCode.BENEFICIARY_NOT_FOUND, exception.getResponseCode());
    }

    @Test
    @DisplayName("가족 정보를 부분 수정한다")
    void updateRecipient() {
        recipientMapper.add(recipient(10L, OWNER_ID, "수정전"));
        RecipientRequest request = new RecipientRequest();
        request.setFamilyName("수정후");
        request.setRelation("OTHER");

        RecipientResponse response = recipientService.updateRecipient(10L, request, OWNER_ID);

        assertEquals("수정후", response.getFamilyName());
        assertEquals("OTHER", response.getRelation());
        assertEquals(1, recipientMapper.updateCount);
    }

    @Test
    @DisplayName("타 사용자가 소유한 가족을 수정할 수 없다")
    void rejectOtherUsersRecipientUpdate() {
        recipientMapper.add(recipient(10L, OTHER_USER_ID, "타인가족"));
        RecipientRequest request = new RecipientRequest();
        request.setFamilyName("수정시도");

        ServiceException exception = assertThrows(
                ServiceException.class,
                () -> recipientService.updateRecipient(10L, request, OWNER_ID)
        );

        assertEquals(ResponseCode.BENEFICIARY_NOT_FOUND, exception.getResponseCode());
        assertEquals(0, recipientMapper.updateCount);
        assertEquals("타인가족", recipientMapper.findStored(10L).getFamilyName());
    }

    @Test
    @DisplayName("Gift가 없는 본인 가족을 삭제한다")
    void deleteRecipientWithoutGift() {
        recipientMapper.add(recipient(10L, OWNER_ID, "삭제대상"));

        recipientService.deleteRecipient(10L, OWNER_ID, false);

        assertNull(recipientMapper.findStored(10L));
        assertEquals(1, giftMapper.countCallCount);
        assertEquals(1, recipientMapper.deleteCount);
    }

    @Test
    @DisplayName("Gift가 존재하면 기본 가족 삭제를 차단한다")
    void rejectDeleteWhenGiftExists() {
        recipientMapper.add(recipient(10L, OWNER_ID, "삭제대상"));
        giftMapper.setGiftCount(10L, 2);

        ServiceException exception = assertThrows(
                ServiceException.class,
                () -> recipientService.deleteRecipient(10L, OWNER_ID, false)
        );

        assertEquals(ResponseCode.CONFLICT, exception.getResponseCode());
        assertNotNull(recipientMapper.findStored(10L));
        assertEquals(1, giftMapper.countCallCount);
        assertEquals(0, recipientMapper.deleteCount);
    }

    @Test
    @DisplayName("force=true이면 Gift 개수 확인 없이 가족을 삭제한다")
    void forceDeleteRecipientWithGift() {
        recipientMapper.add(recipient(10L, OWNER_ID, "강제삭제"));
        giftMapper.setGiftCount(10L, 2);

        recipientService.deleteRecipient(10L, OWNER_ID, true);

        assertNull(recipientMapper.findStored(10L));
        assertEquals(0, giftMapper.countCallCount);
        assertEquals(1, recipientMapper.deleteCount);
    }

    @Test
    @DisplayName("등록 시 필수값이 없거나 관계 값이 잘못되면 검증에 실패한다")
    void rejectInvalidCreateRequest() {
        RecipientRequest missingName = request(null, "OTHER", LocalDate.of(2000, 1, 1));
        RecipientRequest invalidRelation = request("홍길동", "PARENT", LocalDate.of(2000, 1, 1));

        ServiceException missingNameException = assertThrows(
                ServiceException.class,
                () -> recipientService.createRecipient(missingName, OWNER_ID)
        );
        ServiceException invalidRelationException = assertThrows(
                ServiceException.class,
                () -> recipientService.createRecipient(invalidRelation, OWNER_ID)
        );

        assertEquals(ResponseCode.VALIDATION_FAILED, missingNameException.getResponseCode());
        assertEquals(ResponseCode.VALIDATION_FAILED, invalidRelationException.getResponseCode());
        assertEquals(0, recipientMapper.insertCount);
    }

    private RecipientRequest request(String name, String relation, LocalDate birthDate) {
        RecipientRequest request = new RecipientRequest();
        request.setFamilyName(name);
        request.setRelation(relation);
        request.setBirthDate(birthDate);
        request.setFamilyImg("family.png");
        return request;
    }

    private RecipientVO recipient(Long familyId, Long userId, String name) {
        RecipientVO recipient = new RecipientVO();
        recipient.setFamilyId(familyId);
        recipient.setUserId(userId);
        recipient.setFamilyName(name);
        recipient.setRelation("LINEAL_DESCENDANT");
        recipient.setBirthDate(LocalDate.of(2010, 1, 2));
        recipient.setFamilyImg("family.png");
        recipient.setCreatedAt(LocalDateTime.of(2026, 1, 1, 10, 0));
        recipient.setUpdatedAt(LocalDateTime.of(2026, 1, 1, 10, 0));
        return recipient;
    }

    private static class FakeRecipientMapper implements RecipientMapper {

        private final Map<Long, RecipientVO> recipients = new HashMap<>();
        private long sequence = 100L;
        private int insertCount;
        private int updateCount;
        private int deleteCount;

        void add(RecipientVO recipient) {
            recipients.put(recipient.getFamilyId(), recipient);
        }

        RecipientVO findStored(Long familyId) {
            return recipients.get(familyId);
        }

        @Override
        public RecipientVO selectRecipient(Long familyId, Long userId) {
            RecipientVO recipient = recipients.get(familyId);
            return recipient != null && recipient.getUserId().equals(userId) ? recipient : null;
        }

        @Override
        public List<RecipientVO> selectAllRecipient(Long userId) {
            return recipients.values().stream()
                    .filter(recipient -> recipient.getUserId().equals(userId))
                    .sorted(Comparator.comparing(RecipientVO::getFamilyId))
                    .toList();
        }

        @Override
        public void insertRecipient(RecipientVO recipient) {
            insertCount++;
            recipient.setFamilyId(++sequence);
            recipient.setCreatedAt(LocalDateTime.of(2026, 1, 1, 10, 0));
            recipient.setUpdatedAt(LocalDateTime.of(2026, 1, 1, 10, 0));
            recipients.put(recipient.getFamilyId(), recipient);
        }

        @Override
        public void updateRecipient(RecipientVO recipient) {
            updateCount++;
            recipient.setUpdatedAt(LocalDateTime.of(2026, 1, 2, 10, 0));
            recipients.put(recipient.getFamilyId(), recipient);
        }

        @Override
        public void deleteRecipient(Long familyId, Long userId) {
            RecipientVO recipient = selectRecipient(familyId, userId);
            if (recipient != null) {
                recipients.remove(familyId);
                deleteCount++;
            }
        }
    }

    private static class FakeGiftMapper implements GiftMapper {

        private final Map<Long, Integer> giftCounts = new HashMap<>();
        private int countCallCount;

        void setGiftCount(Long familyId, int count) {
            giftCounts.put(familyId, count);
        }

        @Override
        public int insertGift(GiftVO gift) {
            throw new UnsupportedOperationException();
        }

        @Override
        public GiftVO selectGift(Long giftId, Long userId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<GiftVO> selectAllGift(Long familyId, Status status, Long userId) {
            return new ArrayList<>();
        }

        @Override
        public int updateGift(Long giftId, Long amount, LocalDate giftDate, String memo) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int updateGiftStatus(Long giftId, Status status) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int deleteGift(Long giftId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<DeductionVO> selectDeduction(
                Long familyId,
                Long userId,
                LocalDate windowStartDate,
                LocalDate baseDate,
                Long excludeGiftId
        ) {
            return List.of();
        }

        @Override
        public List<GiftVO> selectWindowGifts(
                Long familyId,
                Long userId,
                LocalDate windowStartDate,
                LocalDate baseDate
        ) {
            return List.of();
        }

        @Override
        public TaxBracketVO selectTaxBracket(LocalDate baseDate, Long taxableBase) {
            return null;
        }

        @Override
        public int countGiftByFamily(Long familyId) {
            countCallCount++;
            return giftCounts.getOrDefault(familyId, 0);
        }
    }
}
