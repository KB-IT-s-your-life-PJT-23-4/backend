package com.example.project.ocr.dto.response;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 업로드한 신고서가 등록된 증여 건과 맞는지 대조한 결과.
 *
 * <p>matched 가 true 일 때만 화면에서 증여세 신고서 체크가 자동으로 켜진다. false 면 mismatches 를
 * 보여주고 사용자가 직접 판단하게 둔다. read 는 신고서에서 읽은 원본 값이라 화면에 나란히 보여줄 수 있다.
 */
@Data
public class GiftFilingVerifyResponse {

    private boolean matched;

    private GiftFilingOcrResponse read;

    private List<String> mismatches = new ArrayList<>();
}
