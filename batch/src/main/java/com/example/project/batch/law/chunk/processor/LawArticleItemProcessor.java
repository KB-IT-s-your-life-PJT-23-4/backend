package com.example.project.batch.law.chunk.processor;


import com.example.project.batch.law.chunk.LawUnit;
import com.example.project.batch.law.dto.LawArticle;
import com.example.project.batch.law.dto.UnitType;
import com.example.project.batch.law.dto.raw.AppendixUnit;
import com.example.project.batch.law.dto.raw.ArticleUnit;
import com.example.project.batch.law.dto.raw.CodeValue;
import com.example.project.batch.law.dto.raw.LawBasicInfo;
import lombok.NonNull;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

// 오케스트레이션

/* LawUnit 하나를 적재용 LawArticle 로 바꿈. 조합/태그 분리는 아래 둘에 맡기고, 오케스트레이션/필터링만 함
   <p>적재 대상이 아닌 단위는 null 을 돌려준다. Spring Batch 는 null 을 걸러내고 writer 로 넘기지 않는다.
 * 걸러야 하는 것은 두 종류다.
 * <ul>
 *   <li>조문여부가 "전문"인 편·장·절 헤더. 조문 배열에 섞여 오는데 본문이 "제4절 …" 한 줄뿐이다.</li>
 *   <li>별표구분이 "서식"인 신고서 빈 양식. 별표 배열의 대부분이 이쪽이다.</li>
 * </ul>
*/

@Component
public class LawArticleItemProcessor implements ItemProcessor<LawUnit, LawArticle> {

    // 조문여부, 별표구분 --> 전문 편/장/절 헤더라 적재 x, 서식도 적재 x
    private static final String ARTICLE_FLAG = "조문";
    private static final String APPENDIX_TYPE = "별표";
    private static final DateTimeFormatter RAW_DATE = DateTimeFormatter.BASIC_ISO_DATE;

    // 조문 가지번호 비어있을 경우, 예를 들어 제3조의2의2 면 --> 2만 살림
    private static final String NO_SUB_NO = "0";

    private final ArticleContentAssembler assembler;
    private final RevisionTagParser parser;

    public LawArticleItemProcessor(ArticleContentAssembler assembler, RevisionTagParser parser) {
        this.assembler = assembler;
        this.parser = parser;
    }

    @Nullable
    @Override
    public LawArticle process(@NonNull LawUnit lawUnit) {
        return lawUnit.isArticle() ? toArticle(lawUnit, lawUnit.getArticleUnit()) : toAppendix(lawUnit, lawUnit.getAppendixUnit());
    }

    private LawArticle toArticle(LawUnit lawUnit, ArticleUnit articleUnit) {
        if (!ARTICLE_FLAG.equals(articleUnit.getArticleFlag())) {
            return null;
        }

        LawArticle lawArticle = baseOf(lawUnit);

        lawArticle.setUnitType(UnitType.ARTICLE);
        lawArticle.setArticleNo(articleNo(articleUnit));
        lawArticle.setArticleKey(articleUnit.getArticleKey());
        lawArticle.setTitle(articleUnit.getTitle());
        lawArticle.setArticleEffectiveDate(toLocalDate(articleUnit.getEffectiveDate()));

        applyContent(lawArticle, assembler.assembleArticle(articleUnit));

        return lawArticle;
    }

    private LawArticle toAppendix(LawUnit lawUnit, AppendixUnit appendixUnit) {
        if (!APPENDIX_TYPE.equals(appendixUnit.getAppendixType())) {
            return null;
        }

        LawArticle article = baseOf(lawUnit);

        article.setUnitType(UnitType.APPENDIX);
        article.setArticleNo(appendixNo(appendixUnit));
        article.setArticleKey(appendixUnit.getAppendixKey());
        article.setTitle(appendixUnit.getTitle());

        applyContent(article, assembler.assembleAppendix(appendixUnit));

        return article;
    }

    private LawArticle baseOf(LawUnit lawUnit) {
        LawBasicInfo basicInfo = lawUnit.getBasicInfo();
        LawArticle article = new LawArticle();

        article.setLawCode(basicInfo.getLawId());
        article.setLawKey(lawUnit.getLawKey());
        article.setLawName(basicInfo.getLawName());
        article.setLawType(contentOf(basicInfo.getLawType()));
        article.setMinistry(contentOf(basicInfo.getMinistry()));
        article.setPromulgationNo(basicInfo.getPromulgationNo());
        article.setPromulgationDate(toLocalDate(basicInfo.getPromulgationDate()));
        article.setEffectiveDate(toLocalDate(basicInfo.getEffectiveDate()));

        return article;
    }

    private void applyContent(LawArticle lawArticle, String assembled) {
        RevisionTagParser.Result result = parser.parse(assembled);

        lawArticle.setContent(result.getCleanedContent());
        lawArticle.setRevisionHistory(result.getRevisionHistory());
        lawArticle.setLatestRevisionDate(result.getLatestRevisionDate());
    }

    private String articleNo(ArticleUnit articleUnit) {
        String no = "제" + articleUnit.getArticleNo() + "조";
        String subNo = articleUnit.getArticleSubNo();

        return isBlankNo(subNo) ? no : no + "의" + Integer.parseInt(subNo);
    }

    private String appendixNo(AppendixUnit appendixUnit) {
        String no = "[별표" + (Integer.parseInt(appendixUnit.getAppendixNo()) + 1);
        String subNo = appendixUnit.getAppendixSubNo();

        return isBlankNo(subNo) ? no + "]" : no + "의" + Integer.parseInt(subNo) + "]";
    }

    private boolean isBlankNo(String no) {
        return no == null || no.isBlank() || NO_SUB_NO.equals(String.valueOf(Integer.parseInt(no)));
    }

    private LocalDate toLocalDate(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }

        try {
            return LocalDate.parse(raw.trim(), RAW_DATE);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private String contentOf(CodeValue codeValue) {
        return codeValue == null ? null : codeValue.getContent();
    }
}
