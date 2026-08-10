package com.example.project.gift.dto.request;

import lombok.Data;

/**
 * 저장된 시뮬레이션을 진행 중인 증여로 등록하는 요청.
 *
 * <p>금액·증여일·수증자를 받지 않는다. 전부 시뮬레이션이 이미 계산해 둔 값이라
 * 클라이언트가 다시 실어 보내면 서로 어긋날 여지만 생긴다. 서버가 회차 원본에서 직접 읽는다.
 */
@Data
public class SimulationGiftRequest {

    /** 등록할 시뮬레이션. 저장(SAVED) 상태이고 포트폴리오를 고른 것이어야 한다. */
    private Long simulationId;

    /** 회차마다 같은 값이 붙는다. 미지정 시 서버가 회차 표기로 채운다. */
    private String memo;
}
