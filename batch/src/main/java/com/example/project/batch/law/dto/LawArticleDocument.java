package com.example.project.batch.law.dto;

/*
    임베딩 파이프라인으로 jsonl을 넘길경우를 대비에 만들어놓음, db에도 적재 돼있음

    id/text/metadata 3분할로
    <p>text만 임베딩되고 metadata는 검색/필터/출처 표시에 쓰임

     metadata 는 law_article 에서 content 만 뺀 15컬럼을 담음, content 는 text 에 이미 들어 있어 넣으면 같은 내용이 두 개가 됨.
     metadata 는 임베딩 대상이 아니라 토큰 비용이 붙지 않으므로 필터로 쓸 가능성이 있는 값은 빼서 얻는 게 없다.
* */

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.Getter;

import java.time.LocalDate;

@Getter
@JsonPropertyOrder({"id", "text", "metadata"})
public class LawArticleDocument {

    private final String id;
    private final String text;
    private final Metadata metadata;

    public LawArticleDocument(String id, String text, Metadata metadata) {
        this.id = id;
        this.text = text;
        this.metadata = metadata;
    }

    public static LawArticleDocument from(LawArticle lawArticle) {
        return new LawArticleDocument(idOf(lawArticle), textOf(lawArticle), new Metadata(lawArticle));
    }

    // 법령id + 단위 + 조문키
    private static String idOf(LawArticle lawArticle) {
        return lawArticle.getLawCode() + "-" + lawArticle.getUnitType() + "-" + lawArticle.getArticleKey();
    }

    // 본문 앞에 "법령 제 N조(제목)" 을 넣음.제목 없는 조문이 있어 괄호는 제목이 있을 때만 붙임
    private static String textOf(LawArticle lawArticle) {
        String header = lawArticle.getLawName() + " " + lawArticle.getArticleNo();

        if (lawArticle.getTitle() != null) {
            header = header + "(" + lawArticle.getTitle() + ")";
        }

        return header + "\n" + lawArticle.getContent();
    }

    @Getter
    @JsonPropertyOrder({"law_code", "law_key", "law_name", "law_type", "ministry", "promulgation_no", "promulgation_date", "effective_date",
            "unit_type", "article_no", "article_key", "title", "article_effective_date",
            "revision_history", "latest_revision_date"})
    public static class Metadata {

        @JsonProperty("law_code")
        private final String lawCode;

        @JsonProperty("law_key")
        private final String lawKey;

        @JsonProperty("law_name")
        private final String lawName;

        @JsonProperty("law_type")
        private final String lawType;

        @JsonProperty("ministry")
        private final String ministry;

        @JsonProperty("promulgation_no")
        private final String promulgationNo;

        @JsonProperty("promulgation_date")
        private final String promulgationDate;

        @JsonProperty("effective_date")
        private final String effectiveDate;

        @JsonProperty("unit_type")
        private final String unitType;

        @JsonProperty("article_no")
        private final String articleNo;

        @JsonProperty("article_key")
        private final String articleKey;

        @JsonProperty("title")
        private final String title;

        @JsonProperty("article_effective_date")
        private final String articleEffectiveDate;

        @JsonProperty("revision_history")
        private final String revisionHistory;

        @JsonProperty("latest_revision_date")
        private final String latestRevisionDate;

        private Metadata(LawArticle lawArticle) {
            this.lawCode = lawArticle.getLawCode();
            this.lawKey = lawArticle.getLawKey();
            this.lawName = lawArticle.getLawName();
            this.lawType = lawArticle.getLawType();
            this.ministry = lawArticle.getMinistry();
            this.promulgationNo = lawArticle.getPromulgationNo();
            this.promulgationDate = strDate(lawArticle.getPromulgationDate());
            this.effectiveDate = strDate(lawArticle.getEffectiveDate());
            this.unitType = lawArticle.getUnitType() == null ? null : lawArticle.getUnitType().name();
            this.articleNo = lawArticle.getArticleNo();
            this.articleKey = lawArticle.getArticleKey();
            this.title = lawArticle.getTitle();
            this.articleEffectiveDate = strDate(lawArticle.getArticleEffectiveDate());
            this.revisionHistory = lawArticle.getRevisionHistory();
            this.latestRevisionDate = strDate(lawArticle.getLatestRevisionDate());
        }

        private static String strDate(LocalDate date) {
            return date == null ? null : date.toString();
        }
    }
}
