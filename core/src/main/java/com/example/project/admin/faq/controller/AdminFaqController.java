package com.example.project.admin.faq.controller;

import com.example.project.admin.auth.domain.AdminPrincipal;
import com.example.project.admin.faq.dto.request.AdminCategoryRequest;
import com.example.project.admin.faq.dto.request.AdminFaqRequest;
import com.example.project.admin.faq.dto.response.*;
import com.example.project.admin.faq.service.AdminFaqService;
import com.example.project.common.api.ApiResponse;
import com.example.project.common.api.Pagination;
import com.example.project.common.api.ResponseCode;
import com.example.project.common.logging.ApiLog;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.ApiResponses;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;

@RestController
@Api(
        tags = "관리자 FAQ 관리 API",
        description = "관리자 페이지에서 FAQ와 FAQ 카테고리를 조회·생성·수정·삭제합니다."
)
@ApiLog
@Validated
@RequestMapping("/api/admin/faq")
@RequiredArgsConstructor
public class AdminFaqController {

    private final AdminFaqService adminFaqService;

    @GetMapping
    @ApiOperation(
            value = "FAQ 목록 조회",
            notes = "FAQ 목록을 페이지 단위로 조회합니다. 카테고리와 검색어 조건을 선택적으로 적용할 수 있습니다.",
            authorizations = @io.swagger.annotations.Authorization("Bearer")
    )
    @ApiResponses({
            @io.swagger.annotations.ApiResponse(code = 200, message = "FAQ 목록 조회 성공"),
            @io.swagger.annotations.ApiResponse(code = 400, message = "페이지 또는 검색 조건이 올바르지 않음", response = ApiResponse.class),
            @io.swagger.annotations.ApiResponse(code = 401, message = "인증 실패", response = ApiResponse.class),
            @io.swagger.annotations.ApiResponse(code = 403, message = "관리자 권한 없음", response = ApiResponse.class),
            @io.swagger.annotations.ApiResponse(code = 500, message = "서버 또는 데이터베이스 처리 오류", response = ApiResponse.class)
    })
    public ApiResponse<AdminFaqPageResponse> getFaqPage(
            @ApiParam(value = "0부터 시작하는 페이지 번호", defaultValue = "0", example = "0")
            @RequestParam(defaultValue = "0")
            @Min(0)
            Integer page,

            @ApiParam(value = "페이지당 FAQ 개수", defaultValue = "10", allowableValues = "range[1, 100]", example = "10")
            @RequestParam(defaultValue = "10")
            @Min(1)
            @Max(Pagination.MAX_PAGE_SIZE)
            int size,

            @ApiParam(value = "조회할 카테고리 ID. 생략하면 전체 카테고리를 조회합니다.", example = "1")
            @RequestParam(required = false)
            Long categoryId,

            @ApiParam(value = "FAQ 질문 또는 프롬프트 검색어", example = "증여세 신고")
            @RequestParam(required = false)
            String keyword,

            @AuthenticationPrincipal AdminPrincipal principal,
            HttpServletRequest httpRequest
    ){

        AdminFaqPageResponse response = adminFaqService.getFaqPage(
                page, size, categoryId, keyword
        );

        return ApiResponse.success(ResponseCode.SUCCESS, httpRequest.getRequestURI(), response);
    }

    @GetMapping("/category")
    @ApiOperation(
            value = "FAQ 카테고리 목록 조회",
            notes = "FAQ 등록과 필터에 사용하는 전체 카테고리 목록을 조회합니다.",
            authorizations = @io.swagger.annotations.Authorization("Bearer")
    )
    @ApiResponses({
            @io.swagger.annotations.ApiResponse(code = 200, message = "FAQ 카테고리 목록 조회 성공"),
            @io.swagger.annotations.ApiResponse(code = 401, message = "인증 실패", response = ApiResponse.class),
            @io.swagger.annotations.ApiResponse(code = 403, message = "관리자 권한 없음", response = ApiResponse.class),
            @io.swagger.annotations.ApiResponse(code = 500, message = "서버 또는 데이터베이스 처리 오류", response = ApiResponse.class)
    })
    public ApiResponse<AdminFaqCategoriesDto> getFaqCategories(
            @AuthenticationPrincipal AdminPrincipal principal,
            HttpServletRequest httpRequest
    ){

        AdminFaqCategoriesDto response = adminFaqService.getCategories();

        return ApiResponse.success(ResponseCode.SUCCESS, httpRequest.getRequestURI(), response);
    }
    @PostMapping("/category")
    @ApiOperation(
            value = "FAQ 카테고리 생성",
            notes = "새 FAQ 카테고리를 생성하고 생성된 카테고리 ID를 반환합니다. categoryId는 생략할 수 있습니다.",
            authorizations = @io.swagger.annotations.Authorization("Bearer")
    )
    @ApiResponses({
            @io.swagger.annotations.ApiResponse(code = 200, message = "FAQ 카테고리 생성 성공"),
            @io.swagger.annotations.ApiResponse(code = 400, message = "카테고리 요청값 검증 실패", response = ApiResponse.class),
            @io.swagger.annotations.ApiResponse(code = 401, message = "인증 실패", response = ApiResponse.class),
            @io.swagger.annotations.ApiResponse(code = 403, message = "관리자 권한 없음", response = ApiResponse.class),
            @io.swagger.annotations.ApiResponse(code = 500, message = "서버 또는 데이터베이스 처리 오류", response = ApiResponse.class)
    })
    public ApiResponse<AdminCategoryResponse> createCategory(
            @ApiParam(value = "생성할 FAQ 카테고리 정보", required = true)
            @Valid @RequestBody AdminCategoryRequest request,
            @AuthenticationPrincipal AdminPrincipal principal,
            HttpServletRequest httpRequest
    ){

        AdminCategoryResponse response = adminFaqService.createCategory(request, principal);

        return ApiResponse.success(ResponseCode.SUCCESS, httpRequest.getRequestURI(), response);
    }

    @PatchMapping("/category")
    @ApiOperation(
            value = "FAQ 카테고리 수정",
            notes = "categoryId에 해당하는 FAQ 카테고리 이름을 수정합니다.",
            authorizations = @io.swagger.annotations.Authorization("Bearer")
    )
    @ApiResponses({
            @io.swagger.annotations.ApiResponse(code = 200, message = "FAQ 카테고리 수정 성공"),
            @io.swagger.annotations.ApiResponse(code = 400, message = "카테고리 요청값 검증 실패", response = ApiResponse.class),
            @io.swagger.annotations.ApiResponse(code = 401, message = "인증 실패", response = ApiResponse.class),
            @io.swagger.annotations.ApiResponse(code = 403, message = "관리자 권한 없음", response = ApiResponse.class),
            @io.swagger.annotations.ApiResponse(code = 404, message = "FAQ 카테고리를 찾을 수 없음", response = ApiResponse.class),
            @io.swagger.annotations.ApiResponse(code = 500, message = "서버 또는 데이터베이스 처리 오류", response = ApiResponse.class)
    })
    public ApiResponse<AdminCategoryResponse> updateCategory(
            @ApiParam(value = "수정할 FAQ 카테고리 ID와 이름", required = true)
            @Valid @RequestBody AdminCategoryRequest request,
            @AuthenticationPrincipal AdminPrincipal principal,
            HttpServletRequest httpRequest
    ){
        AdminCategoryResponse response = adminFaqService.updateCategory(request, principal);

        return ApiResponse.success(ResponseCode.SUCCESS, httpRequest.getRequestURI(), response);
    }

    @DeleteMapping("/category/{categoryId}")
    @ApiOperation(
            value = "FAQ 카테고리 삭제",
            notes = "카테고리 ID에 해당하는 FAQ 카테고리를 삭제합니다.",
            authorizations = @io.swagger.annotations.Authorization("Bearer")
    )
    @ApiResponses({
            @io.swagger.annotations.ApiResponse(code = 200, message = "FAQ 카테고리 삭제 성공"),
            @io.swagger.annotations.ApiResponse(code = 400, message = "카테고리 ID가 올바르지 않음", response = ApiResponse.class),
            @io.swagger.annotations.ApiResponse(code = 401, message = "인증 실패", response = ApiResponse.class),
            @io.swagger.annotations.ApiResponse(code = 403, message = "관리자 권한 없음", response = ApiResponse.class),
            @io.swagger.annotations.ApiResponse(code = 404, message = "FAQ 카테고리를 찾을 수 없음", response = ApiResponse.class),
            @io.swagger.annotations.ApiResponse(code = 500, message = "서버 또는 데이터베이스 처리 오류", response = ApiResponse.class)
    })
    public ApiResponse<Void> deleteCategory(
            @ApiParam(value = "삭제할 FAQ 카테고리 ID", required = true, example = "1")
            @Valid @PathVariable long categoryId,
            @AuthenticationPrincipal AdminPrincipal principal,
            HttpServletRequest httpRequest
    ){
        adminFaqService.deleteCategory(categoryId, principal);

        return ApiResponse.success(ResponseCode.SUCCESS, httpRequest.getRequestURI(), null);
    }

    @PostMapping()
    @ApiOperation(
            value = "FAQ 생성",
            notes = "선택한 카테고리에 새 FAQ를 생성하고 생성된 FAQ ID를 반환합니다.",
            authorizations = @io.swagger.annotations.Authorization("Bearer")
    )
    @ApiResponses({
            @io.swagger.annotations.ApiResponse(code = 200, message = "FAQ 생성 성공"),
            @io.swagger.annotations.ApiResponse(code = 400, message = "FAQ 요청값 검증 실패", response = ApiResponse.class),
            @io.swagger.annotations.ApiResponse(code = 401, message = "인증 실패", response = ApiResponse.class),
            @io.swagger.annotations.ApiResponse(code = 403, message = "관리자 권한 없음", response = ApiResponse.class),
            @io.swagger.annotations.ApiResponse(code = 500, message = "서버 또는 데이터베이스 처리 오류", response = ApiResponse.class)
    })
    public ApiResponse<AdminFaqResponse> createFaq(
            @ApiParam(value = "생성할 FAQ 정보", required = true)
            @Valid @RequestBody AdminFaqRequest request,
            @AuthenticationPrincipal AdminPrincipal principal,
            HttpServletRequest httpRequest
    ){
        AdminFaqResponse response = adminFaqService.createFaq(request, principal);

        return ApiResponse.success(ResponseCode.SUCCESS, httpRequest.getRequestURI(), response);
    }

    @PatchMapping("/{faqId}")
    @ApiOperation(
            value = "FAQ 수정",
            notes = "FAQ ID에 해당하는 FAQ의 카테고리, 질문, 프롬프트, 답변과 버튼 표시 여부를 수정합니다.",
            authorizations = @io.swagger.annotations.Authorization("Bearer")
    )
    @ApiResponses({
            @io.swagger.annotations.ApiResponse(code = 200, message = "FAQ 수정 성공"),
            @io.swagger.annotations.ApiResponse(code = 400, message = "FAQ ID 또는 요청값이 올바르지 않음", response = ApiResponse.class),
            @io.swagger.annotations.ApiResponse(code = 401, message = "인증 실패", response = ApiResponse.class),
            @io.swagger.annotations.ApiResponse(code = 403, message = "관리자 권한 없음", response = ApiResponse.class),
            @io.swagger.annotations.ApiResponse(code = 404, message = "FAQ를 찾을 수 없음", response = ApiResponse.class),
            @io.swagger.annotations.ApiResponse(code = 500, message = "서버 또는 데이터베이스 처리 오류", response = ApiResponse.class)
    })
    public ApiResponse<AdminFaqResponse> updateFaq(
            @ApiParam(value = "수정할 FAQ ID", required = true, example = "1")
            @PathVariable long faqId,
            @ApiParam(value = "수정할 FAQ 정보", required = true)
            @Valid @RequestBody AdminFaqRequest request,
            @AuthenticationPrincipal AdminPrincipal principal,
            HttpServletRequest httpRequest
    ){
        AdminFaqResponse response = adminFaqService.updateFaq(faqId, request, principal);

        return ApiResponse.success(ResponseCode.SUCCESS, httpRequest.getRequestURI(), response);
    }

    @DeleteMapping("/{faqId}")
    @ApiOperation(
            value = "FAQ 삭제",
            notes = "FAQ ID에 해당하는 FAQ를 삭제합니다.",
            authorizations = @io.swagger.annotations.Authorization("Bearer")
    )
    @ApiResponses({
            @io.swagger.annotations.ApiResponse(code = 200, message = "FAQ 삭제 성공"),
            @io.swagger.annotations.ApiResponse(code = 400, message = "FAQ ID가 올바르지 않음", response = ApiResponse.class),
            @io.swagger.annotations.ApiResponse(code = 401, message = "인증 실패", response = ApiResponse.class),
            @io.swagger.annotations.ApiResponse(code = 403, message = "관리자 권한 없음", response = ApiResponse.class),
            @io.swagger.annotations.ApiResponse(code = 404, message = "FAQ를 찾을 수 없음", response = ApiResponse.class),
            @io.swagger.annotations.ApiResponse(code = 500, message = "서버 또는 데이터베이스 처리 오류", response = ApiResponse.class)
    })
    public ApiResponse<Void> deleteFaq(
            @ApiParam(value = "삭제할 FAQ ID", required = true, example = "1")
            @Valid @PathVariable long faqId,
            @AuthenticationPrincipal AdminPrincipal principal,
            HttpServletRequest httpRequest
    ){
        adminFaqService.deleteFaq(faqId, principal);

        return ApiResponse.success(ResponseCode.SUCCESS, httpRequest.getRequestURI(), null);
    }
}
