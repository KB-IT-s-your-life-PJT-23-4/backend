package com.example.project.simulation.exception;

import lombok.Getter;

@Getter
public class SimulationException extends RuntimeException {

    private final SimulationError error;
    private final Object data;

    public SimulationException(SimulationError error) {
        super(error.getMessage());
        this.error = error;
        this.data = null;
    }

    public SimulationException(SimulationError error, Object data) {
        super(error.getMessage());
        this.error = error;
        this.data = data;
    }

    public SimulationException(SimulationError error, Throwable cause) {
        super(error.getMessage(), cause);
        this.error = error;
        this.data = null;
    }
}
