package com.managementcontrol.web.agent;

import com.managementcontrol.domain.Device;
import com.managementcontrol.domain.Screenshot;
import com.managementcontrol.service.ScreenshotStorageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;

/** Завантаження скриншотів від агента. */
@RestController
@RequestMapping("/api/v1/agent")
@Tag(name = "Agent API", description = "Ендпоінти, якими користується агент на клієнтському пристрої")
public class AgentScreenshotController {

    private final ScreenshotStorageService storageService;
    private final CurrentDeviceResolver currentDevice;

    public AgentScreenshotController(ScreenshotStorageService storageService,
                                     CurrentDeviceResolver currentDevice) {
        this.storageService = storageService;
        this.currentDevice = currentDevice;
    }

    @Operation(
            summary = "Завантажити скриншот",
            description = "Приймає PNG-файл скриншота у форматі multipart/form-data разом з метаданими. " +
                    "Якщо `takenAt` не вказано або не розпізнано — використовується поточний час сервера. " +
                    "`trigger` зазвичай `AUTO_RANDOM` (заплановане знімання) або `COMMAND` (на вимогу адміністратора)."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Скриншот збережено",
                    content = @Content(schema = @Schema(implementation = Screenshot.class))),
            @ApiResponse(responseCode = "400", description = "Файл відсутній або не є валідним зображенням"),
            @ApiResponse(responseCode = "401", description = "Відсутній або недійсний device-токен")
    })
    @SecurityRequirement(name = "deviceToken")
    @PostMapping(value = "/screenshots", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Screenshot> upload(
            @Parameter(description = "PNG-файл скриншота", required = true)
            @RequestPart("file") MultipartFile file,
            @Parameter(description = "Час знімання у форматі ISO-8601 (напр. 2026-10-30T13:15:00Z). " +
                    "Якщо відсутнє або нерозпізнане — підставляється поточний час сервера.")
            @RequestParam(required = false) String takenAt,
            @Parameter(description = "Причина знімання: `AUTO_RANDOM` або `COMMAND`")
            @RequestParam(required = false) String trigger,
            @Parameter(description = "Ширина зображення в пікселях")
            @RequestParam(required = false) Integer width,
            @Parameter(description = "Висота зображення в пікселях")
            @RequestParam(required = false) Integer height,
            @Parameter(description = "Довільна текстова примітка")
            @RequestParam(required = false) String note) {
        Device device = currentDevice.require();
        Screenshot screenshot = storageService.store(device, file, parseInstant(takenAt),
                trigger, width, height, note);
        return ResponseEntity.status(HttpStatus.CREATED).body(screenshot);
    }

    private Instant parseInstant(String value) {
        if (value == null || value.isBlank()) {
            return Instant.now();
        }
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException e) {
            try {
                return LocalDateTime.parse(value).toInstant(ZoneOffset.UTC);
            } catch (DateTimeParseException ex) {
                return Instant.now();
            }
        }
    }
}