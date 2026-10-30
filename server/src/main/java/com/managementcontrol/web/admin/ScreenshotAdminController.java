package com.managementcontrol.web.admin;

import com.managementcontrol.common.NotFoundException;
import com.managementcontrol.domain.Screenshot;
import com.managementcontrol.repository.ScreenshotRepository;
import com.managementcontrol.service.ScreenshotStorageService;
import com.managementcontrol.web.admin.dto.ScreenshotDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Admin: Screenshots", description = "Перегляд і завантаження скриншотів пристроїв")
@SecurityRequirement(name = "bearerAuth")
public class ScreenshotAdminController {

    private final ScreenshotRepository screenshotRepository;
    private final ScreenshotStorageService storageService;

    public ScreenshotAdminController(ScreenshotRepository screenshotRepository,
                                     ScreenshotStorageService storageService) {
        this.screenshotRepository = screenshotRepository;
        this.storageService = storageService;
    }

    @Operation(
            summary = "Список скриншотів пристрою",
            description = "Повертає метадані скриншотів (без самих зображень), відсортовані за часом " +
                    "знімання у спадному порядку. Опціонально фільтрує за конкретною датою."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Сторінка скриншотів"),
            @ApiResponse(responseCode = "401", description = "Відсутній або недійсний JWT")
    })
    @GetMapping
    public Page<ScreenshotDto> list(
            @Parameter(description = "Внутрішній ID пристрою", required = true)
            @RequestParam Long deviceId,
            @Parameter(description = "Фільтр за конкретною датою (YYYY-MM-DD); якщо не вказано — усі дати")
            @RequestParam(required = false) LocalDate date,
            @Parameter(description = "Номер сторінки, починаючи з 0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Розмір сторінки (1–200)")
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

    @Operation(
            summary = "Завантажити зображення скриншота",
            description = "Повертає бінарний вміст PNG-файлу скриншота. Відповідь кешується на 1 годину."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Зображення PNG",
                    content = @Content(mediaType = "image/png", schema = @Schema(type = "string", format = "binary"))),
            @ApiResponse(responseCode = "401", description = "Відсутній або недійсний JWT"),
            @ApiResponse(responseCode = "404", description = "Скриншот з таким ID не знайдено або файл відсутній на диску")
    })
    @GetMapping("/{id}/file")
    public ResponseEntity<Resource> file(
            @Parameter(description = "ID запису скриншота (не плутати з ID пристрою)")
            @PathVariable Long id) {
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