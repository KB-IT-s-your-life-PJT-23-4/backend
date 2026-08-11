package com.example.project.common.file;

import com.example.project.common.api.ResponseCode;
import com.example.project.common.exception.ServiceException;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
@Log4j2
public class ProfileImageStorageService {

    public static final String PUBLIC_PATH_PREFIX = "/api/profile-images/";
    private static final Pattern STORED_FILE_NAME = Pattern.compile(
            "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.(jpg|png|gif|webp)$"
    );
    private static final Map<String, Set<String>> ALLOWED_EXTENSIONS = Map.of(
            "image/jpeg", Set.of("jpg", "jpeg"),
            "image/png", Set.of("png"),
            "image/gif", Set.of("gif"),
            "image/webp", Set.of("webp")
    );

    private final Path storageDirectory;
    private final long maxFileSize;

    public ProfileImageStorageService(
            @Value("${upload.location}") String uploadLocation,
            @Value("${profile.image.max-size-bytes:10485760}") long maxFileSize
    ) {
        this.storageDirectory = Path.of(uploadLocation).toAbsolutePath().normalize().resolve("profile-images");
        this.maxFileSize = maxFileSize;
    }

    public String store(MultipartFile file) {
        ValidatedImage validatedImage = validate(file);
        String storedFileName = UUID.randomUUID() + "." + validatedImage.extension();
        Path target = resolveStoredFile(storedFileName);
        Path temporary = null;

        try {
            Files.createDirectories(storageDirectory);
            temporary = Files.createTempFile(storageDirectory, "profile-", ".upload");
            try (InputStream inputStream = file.getInputStream()) {
                Files.copy(inputStream, temporary, StandardCopyOption.REPLACE_EXISTING);
            }

            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary, target);
            }

            return PUBLIC_PATH_PREFIX + storedFileName;
        } catch (IOException exception) {
            deletePathQuietly(temporary);
            deletePathQuietly(target);
            throw new ServiceException(ResponseCode.FILE_PROCESSING_ERROR);
        }
    }

    public Resource load(String storedFileName) {
        if (!isStoredFileName(storedFileName)) {
            throw new ServiceException(ResponseCode.RESOURCE_NOT_FOUND);
        }

        try {
            Resource resource = new UrlResource(resolveStoredFile(storedFileName).toUri());
            if (!resource.exists() || !resource.isReadable()) {
                throw new ServiceException(ResponseCode.RESOURCE_NOT_FOUND);
            }
            return resource;
        } catch (IOException exception) {
            throw new ServiceException(ResponseCode.RESOURCE_NOT_FOUND);
        }
    }

    public String contentType(String storedFileName) {
        String extension = extensionOf(storedFileName);
        return switch (extension) {
            case "jpg" -> "image/jpeg";
            case "png" -> "image/png";
            case "gif" -> "image/gif";
            case "webp" -> "image/webp";
            default -> "application/octet-stream";
        };
    }

    public void deleteManagedFile(String publicPath) {
        if (publicPath == null || !publicPath.startsWith(PUBLIC_PATH_PREFIX)) {
            return;
        }

        String storedFileName = publicPath.substring(PUBLIC_PATH_PREFIX.length());
        if (!isStoredFileName(storedFileName)) {
            return;
        }

        try {
            Files.deleteIfExists(resolveStoredFile(storedFileName));
        } catch (IOException exception) {
            log.warn("Could not delete unused profile image: fileName={}", storedFileName);
        }
    }

    public void cleanupAfterSuccessfulUpdate(String previousImage, String storedImage) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            deleteManagedFile(previousImage);
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                deleteManagedFile(previousImage);
            }

            @Override
            public void afterCompletion(int status) {
                if (status != TransactionSynchronization.STATUS_COMMITTED) {
                    deleteManagedFile(storedImage);
                }
            }
        });
    }

    public void deleteAfterCommit(String publicPath) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            deleteManagedFile(publicPath);
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                deleteManagedFile(publicPath);
            }
        });
    }

    private ValidatedImage validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ServiceException(ResponseCode.FILE_FORMAT_INVALID);
        }
        if (file.getSize() > maxFileSize) {
            throw new ServiceException(ResponseCode.FILE_SIZE_EXCEEDED);
        }

        String contentType = normalize(file.getContentType());
        String extension = extensionOf(file.getOriginalFilename());
        Set<String> extensions = ALLOWED_EXTENSIONS.get(contentType);
        if (extensions == null || !extensions.contains(extension) || !hasMatchingSignature(file, contentType)) {
            throw new ServiceException(ResponseCode.FILE_FORMAT_INVALID);
        }

        return new ValidatedImage("jpeg".equals(extension) ? "jpg" : extension);
    }

    private boolean hasMatchingSignature(MultipartFile file, String contentType) {
        byte[] header = new byte[12];
        int length;
        try (InputStream inputStream = file.getInputStream()) {
            length = inputStream.read(header);
        } catch (IOException exception) {
            throw new ServiceException(ResponseCode.FILE_PROCESSING_ERROR);
        }

        return switch (contentType) {
            case "image/jpeg" -> length >= 3
                    && unsigned(header[0]) == 0xff
                    && unsigned(header[1]) == 0xd8
                    && unsigned(header[2]) == 0xff;
            case "image/png" -> length >= 8
                    && unsigned(header[0]) == 0x89
                    && header[1] == 'P'
                    && header[2] == 'N'
                    && header[3] == 'G'
                    && unsigned(header[4]) == 0x0d
                    && unsigned(header[5]) == 0x0a
                    && unsigned(header[6]) == 0x1a
                    && unsigned(header[7]) == 0x0a;
            case "image/gif" -> length >= 6
                    && header[0] == 'G'
                    && header[1] == 'I'
                    && header[2] == 'F'
                    && header[3] == '8'
                    && (header[4] == '7' || header[4] == '9')
                    && header[5] == 'a';
            case "image/webp" -> length >= 12
                    && header[0] == 'R'
                    && header[1] == 'I'
                    && header[2] == 'F'
                    && header[3] == 'F'
                    && header[8] == 'W'
                    && header[9] == 'E'
                    && header[10] == 'B'
                    && header[11] == 'P';
            default -> false;
        };
    }

    private Path resolveStoredFile(String storedFileName) {
        Path resolved = storageDirectory.resolve(storedFileName).normalize();
        if (!resolved.getParent().equals(storageDirectory)) {
            throw new ServiceException(ResponseCode.RESOURCE_NOT_FOUND);
        }
        return resolved;
    }

    private boolean isStoredFileName(String storedFileName) {
        return storedFileName != null && STORED_FILE_NAME.matcher(storedFileName).matches();
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private String extensionOf(String fileName) {
        if (fileName == null) {
            return "";
        }
        String normalizedName = fileName.replace('\\', '/');
        String safeName = normalizedName.substring(normalizedName.lastIndexOf('/') + 1);
        int separator = safeName.lastIndexOf('.');
        return separator < 0 ? "" : safeName.substring(separator + 1).toLowerCase(Locale.ROOT);
    }

    private int unsigned(byte value) {
        return value & 0xff;
    }

    private void deletePathQuietly(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // The original storage failure is more useful to the caller.
        }
    }

    private record ValidatedImage(String extension) {
    }
}
