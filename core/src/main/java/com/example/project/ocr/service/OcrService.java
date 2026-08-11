package com.example.project.ocr.service;

import com.example.project.common.api.ResponseCode;
import com.example.project.common.exception.ServiceException;
import com.example.project.config.ocr.ClovaOcrProperties;
import com.example.project.gift.domain.GiftVO;
import com.example.project.gift.mapper.GiftMapper;
import com.example.project.ocr.client.ClovaOcrClient;
import com.example.project.ocr.domain.OcrBlock;
import com.example.project.ocr.dto.response.GiftFilingOcrResponse;
import com.example.project.ocr.dto.response.GiftFilingVerifyResponse;
import com.example.project.recipient.domain.RecipientVO;
import com.example.project.recipient.mapper.RecipientMapper;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class OcrService {

    private static final Map<String, Set<String>> ALLOWED_EXTENSIONS = Map.of("image/jpeg", Set.of("jpg", "jpeg"),
            "image/png", Set.of("png"),
            "application/pdf", Set.of("pdf"));

    private static final String REQUEST_NAME = "gift_filing";

    private final GiftFilingParser parser;
    private final ClovaOcrClient client;
    private final RecipientMapper recipientMapper;
    private final GiftMapper giftMapper;
    private final ClovaOcrProperties properties;

    public OcrService(GiftFilingParser parser, ClovaOcrClient client, RecipientMapper recipientMapper, GiftMapper giftMapper, ClovaOcrProperties properties) {
        this.parser = parser;
        this.client = client;
        this.recipientMapper = recipientMapper;
        this.giftMapper = giftMapper;
        this.properties = properties;
    }

    /**
     * 신고서를 읽어 등록된 증여 건과 대조한다. 진행 중인 증여의 서류 체크리스트에서 증여세 신고서를
     * 자동으로 켜기 위한 검증이라, 값을 돌려주는 것보다 "이 신고서가 이 증여 건의 것이 맞는지"가 목적이다.
     */
    public GiftFilingVerifyResponse verifyGiftFiling(Long giftId, MultipartFile file, Long userId) {
        if (!properties.isConfigured()) {
            throw new ServiceException(ResponseCode.SERVICE_UNAVAILABLE);
        }

        // 남의 증여 건이면 CLOVA 를 호출하기 전에 끊는다. 인식은 유료고 결과를 쓸 데도 없다.
        GiftVO gift = findOwnedGift(giftId, userId);

        requireUploadable(file);
        byte[] image = bytes(file);
        String format = requiredSupportedFormat(file, image);

        List<OcrBlock> blocks = client.readBlocks(image, format, REQUEST_NAME);

        GiftFilingOcrResponse read = parser.parse(blocks);

        if (read.getGiftDate() == null && read.getAmount() == null) {
            throw new ServiceException(ResponseCode.OCR_FIELD_NOT_FOUND);
        }

        return verify(read, gift, userId);
    }

    private GiftFilingVerifyResponse verify(GiftFilingOcrResponse read, GiftVO gift, Long userId) {
        GiftFilingVerifyResponse result = new GiftFilingVerifyResponse();
        result.setRead(read);

        List<String> mismatches = result.getMismatches();

        if (read.getAmount() == null) {
            mismatches.add("신고서에서 증여 금액을 읽지 못했어요.");
        } else if (!read.getAmount().equals(gift.getAmount())) {
            mismatches.add("금액이 달라요 (신고서 " + won(read.getAmount()) + " / 등록 " + won(gift.getAmount()) + ").");
        }

        if (read.getGiftDate() == null) {
            mismatches.add("신고서에서 증여일을 읽지 못했어요.");
        } else if (!read.getGiftDate().equals(gift.getGiftDate())) {
            mismatches.add("증여일이 달라요 (신고서 " + read.getGiftDate() + " / 등록 " + gift.getGiftDate() + ").");
        }

        verifyRecipient(read, gift, userId, mismatches);

        result.setMatched(mismatches.isEmpty());

        return result;
    }

    /**
     * 수증자 이름은 좌표로 복원한 값이라 오인식이 잦다. 읽었을 때만 대조하고, 못 읽었으면 경고만 남겨
     * 금액·증여일이 맞는 정상 신고서가 이름 때문에 계속 반려되지 않게 한다.
     */
    private void verifyRecipient(GiftFilingOcrResponse read, GiftVO gift, Long userId, List<String> mismatches) {
        String recipientName = read.getRecipientName();

        if (recipientName == null) {
            read.getWarnings().add("신고서에서 수증자 이름을 읽지 못해 대조하지 못했어요.");
            return;
        }

        RecipientVO recipient = recipientMapper.selectRecipient(gift.getFamilyId(), userId);

        if (recipient == null) {
            throw new ServiceException(ResponseCode.BENEFICIARY_NOT_FOUND);
        }

        if (!compact(recipient.getFamilyName()).equals(compact(recipientName))) {
            mismatches.add("수증자가 달라요 (신고서 " + recipientName + " / 등록 " + recipient.getFamilyName() + ").");
        }
    }

    private GiftVO findOwnedGift(Long giftId, Long userId) {
        GiftVO gift = giftMapper.selectGift(giftId, userId);

        if (gift == null) {
            throw new ServiceException(ResponseCode.GIFT_HISTORY_NOT_FOUND);
        }

        return gift;
    }

    private String won(Long amount) {
        return String.format("%,d원", amount);
    }

    private void requireUploadable(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ServiceException(ResponseCode.FILE_FORMAT_INVALID);
        }

        if (file.getSize() > properties.getMaxSizeBytes()) {
            throw new ServiceException(ResponseCode.FILE_SIZE_EXCEEDED);
        }
    }

    private String requiredSupportedFormat(MultipartFile file, byte[] image) {
        String contentType = normalize(file.getContentType());
        String extension = extensionOf(file.getOriginalFilename());
        Set<String> extensions = ALLOWED_EXTENSIONS.get(contentType);

        if (extensions == null || !extensions.contains(extension) || !hasMatchingSignature(contentType, image)) {
            throw new ServiceException(ResponseCode.FILE_FORMAT_INVALID);
        }

        return "jpeg".equals(extension) ? "jpg" : extension;
    }

    private String normalize(String value) {
        // Locale.ROOT는 서버나 os에 무관하게 일관된 소문자 변환
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }


    private String extensionOf(String fileName) {
        if (fileName == null) {
            return "";
        }

        int dot = fileName.lastIndexOf('.');

        return dot < 0 ? "" : normalize(fileName.substring(dot + 1));
    }

    private boolean hasMatchingSignature(String contentType, byte[] image) {
        return switch (contentType) {
            case "image/jpeg" -> image.length >= 3
                    && unsigned(image[0]) == 0xff
                    && unsigned(image[1]) == 0xd8
                    && unsigned(image[2]) == 0xff;
            case "image/png" -> image.length >= 8
                    && unsigned(image[0]) == 0x89
                    && image[1] == 'P'
                    && image[2] == 'N'
                    && image[3] == 'G'
                    && unsigned(image[4]) == 0x0d
                    && unsigned(image[5]) == 0x0a
                    && unsigned(image[6]) == 0x1a
                    && unsigned(image[7]) == 0x0a;
            case "application/pdf" -> image.length >= 5
                    && image[0] == '%'
                    && image[1] == 'P'
                    && image[2] == 'D'
                    && image[3] == 'F'
                    && image[4] == '-';
            default -> false;
        };
    }

    private byte[] bytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new ServiceException(ResponseCode.FILE_PROCESSING_ERROR);
        }
    }

    private String compact(String value) {
        return value == null ? "" : value.replaceAll("\\s+", "");
    }

    private int unsigned(byte value) {
        return value & 0xff;
    }
}
