package com.example.project.user.dto.response;

import lombok.Data;
import lombok.RequiredArgsConstructor;

@Data
@RequiredArgsConstructor
public final class EmailAvailabilityResponse {

    private final boolean available;

    public boolean available() {
        return available;
    }
}
