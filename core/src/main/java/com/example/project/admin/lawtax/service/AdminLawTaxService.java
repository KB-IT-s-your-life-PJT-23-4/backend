package com.example.project.admin.lawtax.service;

import com.example.project.admin.audit.service.AdminAuditWriter;
import com.example.project.admin.auth.domain.AdminPrincipal;
import com.example.project.admin.lawtax.domain.GiftDeductionLimitRow;
import com.example.project.admin.lawtax.domain.GiftTaxBracketRow;
import com.example.project.admin.lawtax.domain.LawArticleRow;
import com.example.project.admin.lawtax.dto.request.AdminGiftTaxVersionRequest;
import com.example.project.admin.lawtax.dto.response.AdminGiftDeductionLimitResponse;
import com.example.project.admin.lawtax.dto.response.AdminGiftTaxBracketResponse;
import com.example.project.admin.lawtax.dto.response.AdminGiftTaxVersionResponse;
import com.example.project.admin.lawtax.dto.response.AdminLawArticleItemResponse;
import com.example.project.admin.lawtax.dto.response.AdminLawArticlePageResponse;
import com.example.project.admin.lawtax.dto.response.AdminLawArticleResponse;
import com.example.project.admin.lawtax.dto.response.AdminLawSummaryResponse;
import com.example.project.admin.lawtax.mapper.AdminLawTaxMapper;
import com.example.project.common.api.Pagination;
import com.example.project.common.api.ResponseCode;
import com.example.project.common.exception.ServiceException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminLawTaxService {

    private final AdminLawTaxMapper adminLawTaxMapper;
    private final AdminAuditWriter adminAuditWriter;

    @Transactional(readOnly = true)
    public List<AdminGiftTaxVersionResponse> getVersions() {
        Map<LocalDate, List<GiftTaxBracketRow>> bracketsByVersion = adminLawTaxMapper.selectAllBrackets().stream()
                .collect(Collectors.groupingBy(GiftTaxBracketRow::getEffectiveFrom, LinkedHashMap::new, Collectors.toList()));
        Map<LocalDate, List<GiftDeductionLimitRow>> limitsByVersion = adminLawTaxMapper.selectAllDeductionLimits().stream()
                .collect(Collectors.groupingBy(GiftDeductionLimitRow::getEffectiveFrom, LinkedHashMap::new, Collectors.toList()));

        LocalDate latest = adminLawTaxMapper.selectMaxBracketEffectiveFrom();

        TreeSet<LocalDate> versionDates = new TreeSet<>(Comparator.reverseOrder());
        versionDates.addAll(bracketsByVersion.keySet());
        versionDates.addAll(limitsByVersion.keySet());

        return versionDates.stream()
                .map(effectiveFrom -> toVersionResponse(
                        effectiveFrom,
                        bracketsByVersion.getOrDefault(effectiveFrom, List.of()),
                        limitsByVersion.getOrDefault(effectiveFrom, List.of()),
                        latest))
                .toList();
    }

    @Transactional
    public AdminGiftTaxVersionResponse createVersion(AdminGiftTaxVersionRequest request, AdminPrincipal actor) {
        validateRequest(request);

        LocalDate effectiveFrom = request.getEffectiveFrom();
        LocalDate latest = adminLawTaxMapper.selectMaxBracketEffectiveFrom();
        if (latest != null && !effectiveFrom.isAfter(latest)) {
            throw new ServiceException(ResponseCode.CONFLICT);
        }

        if (latest != null) {
            adminLawTaxMapper.setBracketEffectiveTo(latest, effectiveFrom);
            adminLawTaxMapper.setDeductionLimitEffectiveTo(latest, effectiveFrom);
        }

        insertRows(effectiveFrom, request);

        adminAuditWriter.record(
                actor,
                "LAWTAX_VERSION_CREATE",
                "GIFT_TAX_VERSION",
                effectiveFrom,
                "증여세 세율·공제 한도 버전을 생성했습니다.",
                Map.of("effectiveFrom", effectiveFrom.toString())
        );

        return getVersion(effectiveFrom);
    }

    @Transactional
    public AdminGiftTaxVersionResponse updateVersion(
            LocalDate effectiveFrom,
            AdminGiftTaxVersionRequest request,
            AdminPrincipal actor
    ) {
        requireEditable(effectiveFrom);
        validateRequest(request);

        adminLawTaxMapper.deleteBracketsByEffectiveFrom(effectiveFrom);
        adminLawTaxMapper.deleteDeductionLimitsByEffectiveFrom(effectiveFrom);
        insertRows(effectiveFrom, request);

        adminAuditWriter.record(
                actor,
                "LAWTAX_VERSION_UPDATE",
                "GIFT_TAX_VERSION",
                effectiveFrom,
                "증여세 세율·공제 한도 버전을 수정했습니다.",
                Map.of("effectiveFrom", effectiveFrom.toString())
        );

        return getVersion(effectiveFrom);
    }

    @Transactional
    public void deleteVersion(LocalDate effectiveFrom, AdminPrincipal actor) {
        requireEditable(effectiveFrom);

        adminLawTaxMapper.deleteBracketsByEffectiveFrom(effectiveFrom);
        adminLawTaxMapper.deleteDeductionLimitsByEffectiveFrom(effectiveFrom);

        LocalDate remaining = adminLawTaxMapper.selectMaxBracketEffectiveFrom();
        if (remaining != null) {
            adminLawTaxMapper.setBracketEffectiveTo(remaining, null);
            adminLawTaxMapper.setDeductionLimitEffectiveTo(remaining, null);
        }

        adminAuditWriter.record(
                actor,
                "LAWTAX_VERSION_DELETE",
                "GIFT_TAX_VERSION",
                effectiveFrom,
                "증여세 세율·공제 한도 버전을 삭제했습니다.",
                Map.of("effectiveFrom", effectiveFrom.toString())
        );
    }

    private void requireEditable(LocalDate effectiveFrom) {
        LocalDate latest = adminLawTaxMapper.selectMaxBracketEffectiveFrom();
        if (latest == null || !latest.equals(effectiveFrom) || !effectiveFrom.isAfter(LocalDate.now())) {
            throw new ServiceException(ResponseCode.CONFLICT);
        }
    }

    private void insertRows(LocalDate effectiveFrom, AdminGiftTaxVersionRequest request) {
        for (AdminGiftTaxVersionRequest.BracketItem bracket : request.getBrackets()) {
            adminLawTaxMapper.insertBracket(
                    effectiveFrom, null,
                    bracket.getLowerBound(), bracket.getUpperBound(),
                    bracket.getTaxRate(), bracket.getProgressiveDeduction()
            );
        }
        for (AdminGiftTaxVersionRequest.DeductionLimitItem limit : request.getDeductionLimits()) {
            adminLawTaxMapper.insertDeductionLimit(
                    effectiveFrom, null,
                    limit.getRelation(), limit.getMinor(), limit.getDeductionLimit()
            );
        }
    }

    private void validateRequest(AdminGiftTaxVersionRequest request) {
        for (AdminGiftTaxVersionRequest.BracketItem bracket : request.getBrackets()) {
            if (bracket.getLowerBound() < 0) {
                throw new ServiceException(ResponseCode.BAD_REQUEST);
            }
            if (bracket.getUpperBound() != null && bracket.getUpperBound() <= bracket.getLowerBound()) {
                throw new ServiceException(ResponseCode.BAD_REQUEST);
            }
            if (bracket.getTaxRate().signum() < 0 || bracket.getTaxRate().doubleValue() > 1) {
                throw new ServiceException(ResponseCode.BAD_REQUEST);
            }
        }
        for (AdminGiftTaxVersionRequest.DeductionLimitItem limit : request.getDeductionLimits()) {
            if (!"LINEAL_DESCENDANT".equals(limit.getRelation()) && !"OTHER".equals(limit.getRelation())) {
                throw new ServiceException(ResponseCode.BAD_REQUEST);
            }
            if ("OTHER".equals(limit.getRelation()) && Boolean.TRUE.equals(limit.getMinor())) {
                throw new ServiceException(ResponseCode.BAD_REQUEST);
            }
        }
    }

    private AdminGiftTaxVersionResponse getVersion(LocalDate effectiveFrom) {
        List<GiftTaxBracketRow> brackets = adminLawTaxMapper.selectBracketsByEffectiveFrom(effectiveFrom);
        List<GiftDeductionLimitRow> limits = adminLawTaxMapper.selectDeductionLimitsByEffectiveFrom(effectiveFrom);
        LocalDate latest = adminLawTaxMapper.selectMaxBracketEffectiveFrom();
        return toVersionResponse(effectiveFrom, brackets, limits, latest);
    }

    private AdminGiftTaxVersionResponse toVersionResponse(
            LocalDate effectiveFrom,
            List<GiftTaxBracketRow> brackets,
            List<GiftDeductionLimitRow> limits,
            LocalDate latest
    ) {
        LocalDate effectiveTo = !brackets.isEmpty()
                ? brackets.get(0).getEffectiveTo()
                : (!limits.isEmpty() ? limits.get(0).getEffectiveTo() : null);

        return AdminGiftTaxVersionResponse.builder()
                .effectiveFrom(effectiveFrom)
                .effectiveTo(effectiveTo)
                .active(effectiveTo == null)
                .editable(effectiveFrom.equals(latest) && effectiveFrom.isAfter(LocalDate.now()))
                .brackets(brackets.stream().map(this::toBracketResponse).toList())
                .deductionLimits(limits.stream().map(this::toDeductionLimitResponse).toList())
                .build();
    }

    private AdminGiftTaxBracketResponse toBracketResponse(GiftTaxBracketRow row) {
        return AdminGiftTaxBracketResponse.builder()
                .bracketId(row.getBracketId())
                .lowerBound(row.getLowerBound())
                .upperBound(row.getUpperBound())
                .taxRate(row.getTaxRate())
                .progressiveDeduction(row.getProgressiveDeduction())
                .build();
    }

    private AdminGiftDeductionLimitResponse toDeductionLimitResponse(GiftDeductionLimitRow row) {
        return AdminGiftDeductionLimitResponse.builder()
                .deductionLimitId(row.getDeductionLimitId())
                .relation(row.getRelation())
                .minor(row.isMinor())
                .deductionLimit(row.getDeductionLimit())
                .build();
    }

    @Transactional(readOnly = true)
    public AdminLawArticlePageResponse getLawArticlePage(int page, int size, String lawCode, String keyword) {
        long offset = Pagination.calculateOffset(page, size);

        long totalCount = adminLawTaxMapper.countLawArticles(lawCode, keyword);
        List<AdminLawArticleItemResponse> items = adminLawTaxMapper
                .selectLawArticlePage(lawCode, keyword, offset, size)
                .stream()
                .map(this::toArticleItemResponse)
                .toList();

        Pagination pagination = Pagination.of(page, size, totalCount, items.size());

        return AdminLawArticlePageResponse.builder()
                .articles(items)
                .pagination(pagination)
                .build();
    }

    @Transactional(readOnly = true)
    public AdminLawArticleResponse getLawArticle(Long lawId) {
        LawArticleRow row = adminLawTaxMapper.selectLawArticleById(lawId);
        if (row == null) {
            throw new ServiceException(ResponseCode.RESOURCE_NOT_FOUND);
        }
        return toArticleResponse(row);
    }

    @Transactional(readOnly = true)
    public List<AdminLawSummaryResponse> getLawSummaries() {
        return adminLawTaxMapper.selectDistinctLaws().stream()
                .map(row -> AdminLawSummaryResponse.builder()
                        .lawCode(row.getLawCode())
                        .lawName(row.getLawName())
                        .lawType(row.getLawType())
                        .build())
                .toList();
    }

    private AdminLawArticleItemResponse toArticleItemResponse(LawArticleRow row) {
        return AdminLawArticleItemResponse.builder()
                .lawId(row.getLawId())
                .lawCode(row.getLawCode())
                .lawName(row.getLawName())
                .lawType(row.getLawType())
                .unitType(row.getUnitType())
                .articleNo(row.getArticleNo())
                .title(row.getTitle())
                .effectiveDate(row.getEffectiveDate())
                .articleEffectiveDate(row.getArticleEffectiveDate())
                .latestRevisionDate(row.getLatestRevisionDate())
                .build();
    }

    private AdminLawArticleResponse toArticleResponse(LawArticleRow row) {
        return AdminLawArticleResponse.builder()
                .lawId(row.getLawId())
                .lawCode(row.getLawCode())
                .lawName(row.getLawName())
                .lawType(row.getLawType())
                .ministry(row.getMinistry())
                .promulgationNo(row.getPromulgationNo())
                .promulgationDate(row.getPromulgationDate())
                .effectiveDate(row.getEffectiveDate())
                .unitType(row.getUnitType())
                .articleNo(row.getArticleNo())
                .title(row.getTitle())
                .articleEffectiveDate(row.getArticleEffectiveDate())
                .content(row.getContent())
                .revisionHistory(row.getRevisionHistory())
                .latestRevisionDate(row.getLatestRevisionDate())
                .build();
    }
}
