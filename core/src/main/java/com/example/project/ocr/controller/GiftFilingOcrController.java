package com.example.project.ocr.controller;

import com.example.project.common.api.ApiResponse;
import com.example.project.common.api.ResponseCode;
import com.example.project.common.logging.ApiLog;
import com.example.project.common.web.CurrentUser;
import com.example.project.ocr.dto.response.GiftFilingVerifyResponse;
import com.example.project.ocr.service.OcrService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.ApiResponses;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;

@ApiLog
@Api(tags = "증여 신고서 OCR API", description = "증여세과세표준신고서 이미지에서 증여 이력 입력값을 추출")
@Log4j2
@RestController
@RequestMapping("/api/gm")
@RequiredArgsConstructor
public class GiftFilingOcrController {

    private final OcrService ocrService;

    @PostMapping(value = "/gift/{giftId}/ocr", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ApiOperation(
            value = "증여 신고서 OCR 대조",
            notes = "신고서 이미지를 읽어 해당 증여 건의 금액·증여일·수증자와 대조합니다. 파일은 저장하지 않습니다. "
                    + "matched 가 true 일 때만 서류 체크리스트의 증여세 신고서를 자동으로 체크하세요. "
                    + "false 면 mismatches 를 보여주고 사용자가 직접 판단하게 해야 합니다. "
                    + "수증자 이름을 읽지 못한 경우는 불일치가 아니라 read.warnings 로 전달됩니다.",
            authorizations = @io.swagger.annotations.Authorization("Bearer")
    )
    @ApiResponses({
            @io.swagger.annotations.ApiResponse(code = 200, message = "대조 완료(일치 여부는 matched 확인)"),
            @io.swagger.annotations.ApiResponse(code = 400, message = "파일 형식 또는 용량 오류", response = ApiResponse.class),
            @io.swagger.annotations.ApiResponse(code = 401, message = "인증 실패", response = ApiResponse.class),
            @io.swagger.annotations.ApiResponse(code = 412, message = "본인 소유 증여 건 없음", response = ApiResponse.class),
            @io.swagger.annotations.ApiResponse(code = 424, message = "신고서에서 증여일과 금액을 찾지 못함", response = ApiResponse.class),
            @io.swagger.annotations.ApiResponse(code = 502, message = "CLOVA OCR 호출 실패", response = ApiResponse.class),
            @io.swagger.annotations.ApiResponse(code = 503, message = "OCR 설정 없음", response = ApiResponse.class)
    })
    public ApiResponse<GiftFilingVerifyResponse> verifyGiftFiling(
            @ApiParam(value = "대조할 증여 건 ID", required = true, example = "1") @PathVariable Long giftId,
            @ApiParam(value = "신고서 JPG, PNG 또는 PDF(최대 10MB)", required = true)
            @RequestPart("file") MultipartFile file,
            @AuthenticationPrincipal String principal,
            HttpServletRequest request
    ) {
        GiftFilingVerifyResponse data = ocrService.verifyGiftFiling(giftId, file, CurrentUser.id(principal));

        return ApiResponse.success(ResponseCode.SUCCESS, request.getRequestURI(), data);
    }
}
