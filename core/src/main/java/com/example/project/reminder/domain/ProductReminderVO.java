package com.example.project.reminder.domain;

import com.example.project.simulation.domain.ProductType;
import lombok.Data;

import java.time.LocalDate;

/**
 * 만기 알림의 재료 한 줄. (증여, 상품) 한 쌍이라 상품 수만큼 행이 나온다.
 *
 * <p>만기일은 상품이 아니라 시뮬레이션이 갖는다({@code simulation.investment_end_date}).
 * 예금·적금 테이블에는 가입 가능 기간의 범위(min_month~max_month)만 있고 실제로 고른 기간은
 * {@code simulation.investment_period_months} 하나뿐이라, 한 포트폴리오의 상품들은 만기를 공유한다.
 * 그래서 giftId 가 같은 행끼리 묶으면 알림 한 건이 된다.
 */
@Data
public class ProductReminderVO {

    /**
     * 알림을 매다는 증여. 그 시뮬레이션에서 등록된 증여 중 회차가 가장 빠른 한 건이다.
     *
     * <p>리마인더는 (giftId, type) 으로 지목하는데 만기는 시뮬레이션당 하나뿐이라
     * 회차마다 매달면 같은 알림이 회차 수만큼 뜬다.
     */
    private Long giftId;

    /**
     * 수증자. 이름은 담지 않는다 — {@code family.family_name_encrypted} 라 SQL 로는 못 읽고,
     * 서비스가 이미 복호화해 둔 공제 조회 결과에서 이 id 로 찾는다.
     */
    private Long familyId;

    /** {@code simulation.investment_end_date}. 같은 시뮬레이션의 모든 상품이 같은 값이다. */
    private LocalDate maturityDate;

    private String productName;

    /** DEPOSIT / SAVINGS. ETF 는 만기가 없어 조회 단계에서 빠진다. */
    private ProductType productType;
}
