package com.example.project.common.file;

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
@RequestMapping("/api/profile-images")
@RequiredArgsConstructor
public class ProfileImageController {

    private final ProfileImageStorageService profileImageStorageService;

    @GetMapping("/{fileName:.+}")
    public ResponseEntity<Resource> getProfileImage(@PathVariable String fileName) {
        Resource resource = profileImageStorageService.load(fileName);
        MediaType mediaType = MediaType.parseMediaType(profileImageStorageService.contentType(fileName));

        return ResponseEntity.ok()
                .contentType(mediaType)
                .cacheControl(CacheControl.maxAge(365, TimeUnit.DAYS).cachePublic())
                .body(resource);
    }
}
