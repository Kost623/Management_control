package com.managementcontrol.service;

import com.managementcontrol.config.ManagementControlProperties;
import com.managementcontrol.domain.Device;
import com.managementcontrol.domain.Screenshot;
import com.managementcontrol.repository.ScreenshotRepository;
import com.managementcontrol.common.NotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * Зберігання скриншотів на файловій системі сервера.
 * Структура: {root}/{deviceId}/{yyyy-MM-dd}/{uuid}.png
 */
@Service
public class ScreenshotStorageService {

    private static final Logger log = LoggerFactory.getLogger(ScreenshotStorageService.class);

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneOffset.UTC);

    private final ScreenshotRepository screenshotRepository;
    private final ManagementControlProperties properties;
    private final Path root;

    public ScreenshotStorageService(ScreenshotRepository screenshotRepository,
                                    ManagementControlProperties properties) {
        this.screenshotRepository = screenshotRepository;
        this.properties = properties;
        this.root = Paths.get(properties.getStorage().getScreenshotsPath()).toAbsolutePath().normalize();
        try {
            Files.createDirectories(root);
        } catch (IOException e) {
            throw new IllegalStateException("Не вдалося створити каталог для скриншотів: " + root, e);
        }
        log.info("Сховище скриншотів: {}", root);
    }

    @Transactional
    public Screenshot store(Device device, MultipartFile file, Instant takenAt, String trigger,
                            Integer width, Integer height, String note) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Файл скриншота порожній");
        }
        String extension = resolveExtension(file.getOriginalFilename());
        String day = DAY.format(takenAt == null ? Instant.now() : takenAt);
        String fileName = UUID.randomUUID() + extension;

        Path target = root.resolve(device.getDeviceId()).resolve(day).resolve(fileName).normalize();
        if (!target.startsWith(root)) {
            throw new IllegalArgumentException("Некоректний шлях файлу");
        }

        long size;
        String sha256;
        try {
            Files.createDirectories(target.getParent());
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream in = file.getInputStream();
                 DigestInputStream digestStream = new DigestInputStream(in, digest)) {
                size = Files.copy(digestStream, target, StandardCopyOption.REPLACE_EXISTING);
            }
            sha256 = hex(digest.digest());
        } catch (IOException e) {
            throw new IllegalStateException("Помилка збереження скриншота", e);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 недоступний", e);
        }

        Screenshot screenshot = new Screenshot();
        screenshot.setDeviceId(device.getId());
        screenshot.setFileName(fileName);
        screenshot.setStoragePath(root.relativize(target).toString());
        screenshot.setSizeBytes(size);
        screenshot.setSha256(sha256);
        screenshot.setTakenAt(takenAt == null ? Instant.now() : takenAt);
        screenshot.setTriggerType(trigger == null ? "AUTO_RANDOM" : trigger);
        screenshot.setWidth(width);
        screenshot.setHeight(height);
        screenshot.setNote(note);
        return screenshotRepository.save(screenshot);
    }

    @Transactional(readOnly = true)
    public Resource load(Screenshot screenshot) {
        Path path = root.resolve(screenshot.getStoragePath()).normalize();
        if (!path.startsWith(root) || !Files.exists(path)) {
            throw new NotFoundException("Файл скриншота не знайдено");
        }
        try {
            return new UrlResource(path.toUri());
        } catch (IOException e) {
            throw new IllegalStateException("Не вдалося відкрити файл скриншота", e);
        }
    }

    public Path getRoot() {
        return root;
    }

    private String resolveExtension(String originalName) {
        if (originalName == null) {
            return ".png";
        }
        int dot = originalName.lastIndexOf('.');
        if (dot < 0 || dot == originalName.length() - 1) {
            return ".png";
        }
        String ext = originalName.substring(dot).toLowerCase();
        return switch (ext) {
            case ".png", ".jpg", ".jpeg", ".bmp", ".webp" -> ext;
            default -> ".png";
        };
    }

    private String hex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16));
            sb.append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }
}
