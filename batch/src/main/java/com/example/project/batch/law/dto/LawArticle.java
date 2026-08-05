package com.example.project.batch.law.dto;

import lombok.Data;

import java.time.LocalDate;

@Data
public class LawArticle {

    private String lawCode; // 국가법령정보 법령id. 개정돼도 불변
    private String lawKey;  // 법령키 = 법령ID+시행일자+공포번호. 버전 유일 식별
    private String lawName;
    private String lawType; // 법률/대통령령/재경부령
    private String ministry;
    private String promulgationNo;  // 공포 넘버
    private LocalDate promulgationDate;
    private LocalDate effectiveDate; // 법령의 유효 일자(기본 정보)

    private UnitType unitType;
    private String articleNo;
    private String articleKey;
    private String title;
    private LocalDate articleEffectiveDate; // 이 조문이 효력을 갖는 날(조문 단위)

    private String content; // 조문내용 + 항 + 호 + 목 조합
    private String revisionHistory; // 개정이력
    private LocalDate latestRevisionDate; // 최근 개정일자
}
