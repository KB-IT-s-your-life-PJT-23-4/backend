package com.example.project.config.ocr;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class ClovaOcrProperties {

    private final String invokeUrl;
    private final String secret;
    private final long maxSizeBytes;

    public boolean isConfigured() {
        return invokeUrl != null && !invokeUrl.isBlank() && secret != null && !secret.isBlank();
    }
}
