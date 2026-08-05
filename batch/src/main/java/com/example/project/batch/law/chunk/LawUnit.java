package com.example.project.batch.law.chunk;

import com.example.project.batch.law.dto.raw.ArticleUnit;
import com.example.project.batch.law.dto.raw.AppendixUnit;
import com.example.project.batch.law.dto.raw.LawBasicInfo;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Reader 가 방출하고 Processor 가 받는 중간 타입.
 *
 * <p>법령 하나의 조문·별표를 낱개로 흘리기 위한 래퍼다. 법령 메타({@link LawBasicInfo})와
 * 그 아래 단위 하나(조문 또는 별표)를 함께 싣는다. 메타는 파싱 전 raw 그대로 나르고,
 * 날짜 파싱·본문 조립은 Processor 가 {@code LawArticle} 로 변환하며 처리한다.
 *
 * <p>법령키({@link #lawKey})는 응답 최상위의 "법령키" 값이다. 기본정보에는 없어서 따로 받는다.
 *
 * <p>조문 단위는 {@link #articleUnit}, 별표 단위는 {@link #appendixUnit} 에 담기며
 * 항상 둘 중 하나만 채워진다. Processor 는 어느 쪽이 채워졌는지로 분기한다.
 */
@Getter
@AllArgsConstructor
public class LawUnit {

    private final LawBasicInfo basicInfo;
    private final String lawKey;

    /** 조문일 때만 채워진다. 별표면 null. */
    private final ArticleUnit articleUnit;

    /** 별표일 때만 채워진다. 조문이면 null. */
    private final AppendixUnit appendixUnit;

    public static LawUnit ofArticle(LawBasicInfo basicInfo, String lawKey, ArticleUnit unit) {
        return new LawUnit(basicInfo, lawKey, unit, null);
    }

    public static LawUnit ofAppendix(LawBasicInfo basicInfo, String lawKey, AppendixUnit unit) {
        return new LawUnit(basicInfo, lawKey, null, unit);
    }

    public boolean isArticle() {
        return articleUnit != null;
    }
}
