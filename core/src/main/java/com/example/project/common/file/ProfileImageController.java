package com.example.project.common.file;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.TimeUnit;

@RestController
@Api(tags = "프로필 이미지 API", description = "저장된 사용자 및 수증자 프로필 이미지를 조회합니다.")
@RequestMapping("/api/profile-images")
@RequiredArgsConstructor
public class ProfileImageController {

    private final ProfileImageStorageService profileImageStorageService;

    @GetMapping("/{fileName:.+}")
    @ApiOperation(value = "프로필 이미지 조회", notes = "파일명으로 프로필 이미지를 조회하며 공개 캐시 헤더를 반환합니다.")
    public ResponseEntity<Resource> getProfileImage(
            @ApiParam(value = "조회할 프로필 이미지 파일명", required = true, example = "profile.png")
            @PathVariable String fileName
    ) {
        Resource resource = profileImageStorageService.load(fileName);
        MediaType mediaType = MediaType.parseMediaType(profileImageStorageService.contentType(fileName));

        return ResponseEntity.ok()
                .contentType(mediaType)
                .cacheControl(CacheControl.maxAge(365, TimeUnit.DAYS).cachePublic())
                .body(resource);
    }
}
