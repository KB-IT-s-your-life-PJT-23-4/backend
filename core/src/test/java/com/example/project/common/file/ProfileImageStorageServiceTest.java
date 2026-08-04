package com.example.project.common.file;

import com.example.project.common.api.ResponseCode;
import com.example.project.common.exception.ServiceException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProfileImageStorageServiceTest {

    @TempDir
    Path tempDirectory;

    @Test
    void storesValidImageWithGeneratedNameAndLoadsIt() throws Exception {
        ProfileImageStorageService service = new ProfileImageStorageService(
                tempDirectory.toString(),
                10 * 1024 * 1024
        );

        String publicPath = service.store(pngFile("profile.png"));
        String storedFileName = publicPath.substring(ProfileImageStorageService.PUBLIC_PATH_PREFIX.length());

        assertTrue(publicPath.matches("/api/profile-images/[0-9a-f-]{36}\\.png"));
        assertTrue(service.load(storedFileName).isReadable());
        assertEquals("image/png", service.contentType(storedFileName));
    }

    @Test
    void rejectsExtensionAndMimeTypeMismatch() {
        ProfileImageStorageService service = new ProfileImageStorageService(tempDirectory.toString(), 1024);
        MockMultipartFile invalid = new MockMultipartFile(
                "image",
                "profile.jpg",
                "image/png",
                pngBytes()
        );

        ServiceException exception = assertThrows(ServiceException.class, () -> service.store(invalid));

        assertEquals(ResponseCode.FILE_FORMAT_INVALID, exception.getResponseCode());
    }

    @Test
    void rejectsOversizedImageBeforeWriting() {
        ProfileImageStorageService service = new ProfileImageStorageService(tempDirectory.toString(), 4);

        ServiceException exception = assertThrows(
                ServiceException.class,
                () -> service.store(pngFile("profile.png"))
        );

        assertEquals(ResponseCode.FILE_SIZE_EXCEEDED, exception.getResponseCode());
    }

    @Test
    void rejectsPathTraversalWhenLoading() {
        ProfileImageStorageService service = new ProfileImageStorageService(tempDirectory.toString(), 1024);

        ServiceException exception = assertThrows(
                ServiceException.class,
                () -> service.load("../profile.png")
        );

        assertEquals(ResponseCode.RESOURCE_NOT_FOUND, exception.getResponseCode());
    }

    static MockMultipartFile pngFile(String fileName) {
        return new MockMultipartFile("image", fileName, "image/png", pngBytes());
    }

    private static byte[] pngBytes() {
        return new byte[]{
                (byte) 0x89, 'P', 'N', 'G', 0x0d, 0x0a, 0x1a, 0x0a,
                0x00, 0x00, 0x00, 0x0d
        };
    }
}
