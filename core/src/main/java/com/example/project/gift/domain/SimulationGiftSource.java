package com.example.project.gift.domain;

import lombok.Data;

/**
 * 저장된 시뮬레이션을 증여로 등록할 때 필요한 최소 정보.
 *
 * <p>회차 정보는 {@code simulation_tranche} 에 있는데 그 테이블은 시나리오(result) 단위로 묶여 있고,
 * 화면이 들고 있는 것은 시뮬레이션 id 하나뿐이다. 그래서 사용자가 고른 포트폴리오
 * ({@code simulation.selected_portfolio_id})를 거쳐 시나리오를 되짚어야 회차를 찾을 수 있다.
 * 그 조회 결과를 담는다.
 */
@Data
public class SimulationGiftSource {

    /** 증여를 받을 수증자. 회차가 몇 건이든 수증자는 하나다. */
    private Long familyId;

    /** 사용자가 선택한 시나리오. 등록되는 gift 행 전부가 이 값을 공유한다. */
    private Long simulResultId;
}
