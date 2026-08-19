package com.example.project.simulation.domain;

public enum RiskProfile {
    CONSERVATIVE,
    BALANCED,
    AGGRESSIVE,
    CUSTOM;

    private static final RiskProfile[] PRESET_VALUES = {
            CONSERVATIVE,
            BALANCED,
            AGGRESSIVE
    };

    public static RiskProfile[] presetValues() {
        return PRESET_VALUES.clone();
    }

    public boolean isPreset() {
        return this != CUSTOM;
    }
}
