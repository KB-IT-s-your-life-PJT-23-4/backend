package com.example.project.gift.domain;

import java.util.EnumSet;
import java.util.Set;

public enum Status {
    PLANNED,
    COMPLETED,
    CANCELLED;

    public Set<Status> allowedTransactions() {
        return switch (this) {
            case PLANNED -> EnumSet.of(COMPLETED, CANCELLED);
            case COMPLETED -> EnumSet.of(CANCELLED);
            case CANCELLED -> EnumSet.noneOf(Status.class);
        };
    }

    public boolean canTransitionTo(Status target) {
        return allowedTransactions().contains(target);
    }

    public boolean deletable() {
        return this == PLANNED;
    }
}
