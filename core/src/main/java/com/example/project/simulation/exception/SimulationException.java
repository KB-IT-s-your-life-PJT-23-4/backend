package com.example.project.simulation.exception;

import lombok.Getter;

@Getter
public class SimulationException extends RuntimeException {

    private final SimulationError error;

    public SimulationException(SimulationError error) {
        super(error.getMessage());
        this.error = error;
    }

    public SimulationException(SimulationError error, Throwable cause) {
        super(error.getMessage(), cause);
        this.error = error;
    }
}
