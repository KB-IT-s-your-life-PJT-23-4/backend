package com.example.project.gift.domain;

import java.util.EnumSet;
import java.util.Set;

public enum Status {
    PLANNED,
    COMPLETED,
    CANCELLED;

    /** 변경 가능한 증여 상태 */
    public Set<Status> allowedTransactions() {
        return switch (this) {
            case PLANNED -> EnumSet.of(COMPLETED, CANCELLED);
            case COMPLETED -> EnumSet.of(CANCELLED);
            case CANCELLED -> EnumSet.noneOf(Status.class);
        };
    }

    /** 상태 변경이 가능한지 */
    public boolean canTransitionTo(Status target) {
        return allowedTransactions().contains(target);
    }
}
