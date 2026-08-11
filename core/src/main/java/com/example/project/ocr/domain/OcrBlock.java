package com.example.project.ocr.domain;

import lombok.Data;
import lombok.RequiredArgsConstructor;

/**
 * OCR 이 인식한 텍스트 조각 하나와 그 위치.
 * <p>좌표를 함께 들고 다니는 이유는 신고서가 표이기 때문이다. CLOVA 는
 칸 단위로 조각을 돌려주므로 텍스트만 이어 붙이면 "항목명"과 "금액"이 어느 행에 속했는지가 사라진
 다. y 로 행을 묶고 x 로 정렬해야 "⑮증여재산가액 | 20,000,000" 같은 한 줄이 복원된다.
 */
@Data
public class OcrBlock {

    private String text;
    private double x;
    private double y;
    private double height;
}
