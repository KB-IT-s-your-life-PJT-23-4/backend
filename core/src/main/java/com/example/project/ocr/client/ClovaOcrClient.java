package com.example.project.ocr.client;

import com.example.project.common.api.ResponseCode;
import com.example.project.common.exception.ServiceException;
import com.example.project.config.ocr.ClovaOcrProperties;
import com.example.project.ocr.domain.OcrBlock;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.BodyInserter;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** CLOVA OCR General 호출. 템플릿 없이 이미지 전체 텍스트를 좌표와 받아옴 */
@Log4j2
@Component
public class ClovaOcrClient {

    private static final String SECRET_HEADER = "X-OCR-SECRET";
    private final WebClient clovaOcrWebClient;
    private final ClovaOcrProperties properties;

    public ClovaOcrClient(@Qualifier("clovaOcrWebClient") WebClient clovaOcrWebClient, ClovaOcrProperties properties) {
        this.clovaOcrWebClient = clovaOcrWebClient;
        this.properties = properties;
    }

    public List<OcrBlock> readBlocks(byte[] image, String format, String name) {
        MultipartBodyBuilder body = new MultipartBodyBuilder();
        body.part("message", message(format, name)).contentType(MediaType.APPLICATION_JSON);
        body.part("file", new ByteArrayResource(image) {
            @Override
            public String getFilename() {
                return name + "." + format;
            }
        });

        JsonNode response;
        try {
            response = clovaOcrWebClient.post()
                    .uri(properties.getInvokeUrl())
                    .header(SECRET_HEADER, properties.getSecret())
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(BodyInserters.fromMultipartData(body.build()))
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();
        } catch (RuntimeException exception) {
            log.error("clova ocr 호출 실패", exception);
            throw new ServiceException(ResponseCode.EXTERNAL_API_ERROR);
        }

        return toOcrBlocks(response);
    }

    private Map<String, Object> message(String format, String name) {
        return Map.of(
                "version", "V2",
                "requestId", UUID.randomUUID().toString(),
                "timestamp", System.currentTimeMillis(),
                "lang", "ko",
                "images", List.of(Map.of("format", format, "name", name))
        );
    }

    private List<OcrBlock> toOcrBlocks(JsonNode response) {
        JsonNode images = response == null ? null : response.path("images");

        if (images == null || !images.isArray() || images.isEmpty()) {
            throw new ServiceException(ResponseCode.EXTERNAL_API_ERROR);
        }
        JsonNode image = images.get(0);
        if (!"SUCCESS".equals(image.path("inferResult").asText())) {
            log.warn("clova ocr 인식 실패, message = {}", image.path("message").asText());
            throw new ServiceException(ResponseCode.OCR_FIELD_NOT_FOUND);
        }

        List<OcrBlock> blocks = new ArrayList<>();

        for (JsonNode field : image.path("fields")) {
            String text = field.path("inferText").asText("").trim();
            JsonNode vertices = field.path("boundingPoly").path("vertices");

            if (text.isEmpty() || !vertices.isArray() || vertices.isEmpty()) {
                continue;
            }

            double minX = Double.MAX_VALUE;
            double minY = Double.MAX_VALUE;
            double maxY = -Double.MAX_VALUE;

            for (JsonNode vertex : vertices) {
                double x = vertex.path("x").asDouble();
                double y = vertex.path("y").asDouble();

                minX = Math.min(x, minX);
                minY = Math.min(y, minY);
                maxY = Math.max(y, maxY);
            }

            OcrBlock block = new OcrBlock();
            block.setText(text);
            block.setX(minX);
            block.setY((minY + maxY) / 2);
            block.setHeight(Math.max(1, maxY - minY));
            blocks.add(block);
        }

        return blocks;
    }
}
