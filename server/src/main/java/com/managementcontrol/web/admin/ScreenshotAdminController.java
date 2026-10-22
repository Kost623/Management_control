package com.managementcontrol.web.admin;

import com.managementcontrol.common.NotFoundException;
import com.managementcontrol.domain.Screenshot;
import com.managementcontrol.repository.ScreenshotRepository;
import com.managementcontrol.service.ScreenshotStorageService;
import com.managementcontrol.web.admin.dto.ScreenshotDto;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.concurrent.TimeUnit;

/** Перегляд скриншотів, зроблених агентом. */
@RestController
@RequestMapping("/api/v1/admin/screenshots")
public class ScreenshotAdminController {

    private final ScreenshotRepository screenshotRepository;
    private final ScreenshotStorageService storageService;

    public ScreenshotAdminController(ScreenshotRepository screenshotRepository,
                                     ScreenshotStorageService storageService) {
        this.screenshotRepository = screenshotRepository;
        this.storageService = storageService;
    }

    @GetMapping
    public Page<ScreenshotDto> list(@RequestParam Long deviceId,
                                    @RequestParam(required = false) LocalDate date,
                                    @RequestParam(defaultValue = "0") int page,
                                    @RequestParam(defaultValue = "50") int size) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 200),
                Sort.by(Sort.Direction.DESC, "takenAt"));
        Page<Screenshot> result;
        if (date != null) {
            Instant from = date.atStartOfDay(ZoneOffset.UTC).toInstant();
            Instant to = date.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
            result = screenshotRepository.findByDeviceIdAndTakenAtBetweenOrderByTakenAtDesc(
                    deviceId, from, to, pageable);
        } else {
            result = screenshotRepository.findByDeviceIdOrderByTakenAtDesc(deviceId, pageable);
        }
        return result.map(ScreenshotAdminController::toDto);
    }

    /** Завантаження зображення. */
    @GetMapping("/{id}/file")
    public ResponseEntity<Resource> file(@PathVariable Long id) {
        Screenshot screenshot = screenshotRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Screenshot", id));
        Resource resource = storageService.load(screenshot);
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .cacheControl(CacheControl.maxAge(1, TimeUnit.HOURS))
                .body(resource);
    }

    private static ScreenshotDto toDto(Screenshot screenshot) {
        return new ScreenshotDto(screenshot.getId(), screenshot.getDeviceId(), screenshot.getFileName(),
                screenshot.getSizeBytes(), screenshot.getTakenAt(), screenshot.getTriggerType(),
                screenshot.getWidth(), screenshot.getHeight(), screenshot.getNote(),
                "/api/v1/admin/screenshots/" + screenshot.getId() + "/file");
    }
}
