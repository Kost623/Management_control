package com.managementcontrol.web.agent;

import com.managementcontrol.domain.Device;
import com.managementcontrol.domain.Screenshot;
import com.managementcontrol.service.ScreenshotStorageService;
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
public class AgentScreenshotController {

    private final ScreenshotStorageService storageService;
    private final CurrentDeviceResolver currentDevice;

    public AgentScreenshotController(ScreenshotStorageService storageService,
                                     CurrentDeviceResolver currentDevice) {
        this.storageService = storageService;
        this.currentDevice = currentDevice;
    }

    @PostMapping(value = "/screenshots", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Screenshot> upload(@RequestPart("file") MultipartFile file,
                                             @RequestParam(required = false) String takenAt,
                                             @RequestParam(required = false) String trigger,
                                             @RequestParam(required = false) Integer width,
                                             @RequestParam(required = false) Integer height,
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
