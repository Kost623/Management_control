package com.managementcontrol.web.admin;

import com.managementcontrol.domain.BrowsingActivity;
import com.managementcontrol.domain.UsageSession;
import com.managementcontrol.repository.BrowsingActivityRepository;
import com.managementcontrol.repository.UsageSessionRepository;
import com.managementcontrol.web.admin.dto.BrowsingActivityDto;
import com.managementcontrol.web.admin.dto.UsageSessionDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

/**
 * Сирі дані по пристрою за період: час використання програм та відвідування сайтів.
 */
@RestController
@RequestMapping("/api/v1/admin/devices")
@Tag(name = "Admin: Device Activity", description = "Сирі дані телеметрії пристрою за період")
@SecurityRequirement(name = "bearerAuth")
public class DeviceActivityController {

    private final UsageSessionRepository usageSessionRepository;
    private final BrowsingActivityRepository browsingActivityRepository;

    public DeviceActivityController(UsageSessionRepository usageSessionRepository,
                                    BrowsingActivityRepository browsingActivityRepository) {
        this.usageSessionRepository = usageSessionRepository;
        this.browsingActivityRepository = browsingActivityRepository;
    }

    @Operation(
            summary = "Сесії використання програм за період",
            description = "Повертає інтервали активності програм для пристрою. Якщо `from`/`to` не вказані — " +
                    "за замовчуванням береться діапазон 'вчора — завтра' (UTC)."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Сторінка сесій"),
            @ApiResponse(responseCode = "401", description = "Відсутній або недійсний JWT")
    })
    @GetMapping("/{deviceId}/usage-sessions")
    public Page<UsageSessionDto> usageSessions(
            @Parameter(description = "Внутрішній ID пристрою")
            @PathVariable Long deviceId,
            @Parameter(description = "Початок періоду (YYYY-MM-DD, включно). За замовчуванням — вчора")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @Parameter(description = "Кінець періоду (YYYY-MM-DD, включно). За замовчуванням — завтра")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @Parameter(description = "Номер сторінки, починаючи з 0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Розмір сторінки (1–500)")
            @RequestParam(defaultValue = "100") int size) {
        Instant[] range = resolveRange(from, to);
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 500));
        List<UsageSession> all = usageSessionRepository
                .findByDeviceIdAndStartedAtBetweenOrderByStartedAtAsc(deviceId, range[0], range[1]);
        List<UsageSessionDto> content = all.stream()
                .skip(pageable.getOffset())
                .limit(pageable.getPageSize())
                .map(DeviceActivityController::toDto)
                .toList();
        return new PageImpl<>(content, pageable, all.size());
    }

    @Operation(
            summary = "Активність браузера за період",
            description = "Повертає відвідування сайтів для пристрою. Діапазон за замовчуванням той самий, " +
                    "що й у /usage-sessions."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Сторінка записів активності браузера"),
            @ApiResponse(responseCode = "401", description = "Відсутній або недійсний JWT")
    })
    @GetMapping("/{deviceId}/browsing")
    public Page<BrowsingActivityDto> browsing(
            @Parameter(description = "Внутрішній ID пристрою")
            @PathVariable Long deviceId,
            @Parameter(description = "Початок періоду (YYYY-MM-DD, включно)")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @Parameter(description = "Кінець періоду (YYYY-MM-DD, включно)")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @Parameter(description = "Номер сторінки, починаючи з 0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Розмір сторінки (1–500)")
            @RequestParam(defaultValue = "100") int size) {
        Instant[] range = resolveRange(from, to);
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 500));
        List<BrowsingActivity> all = browsingActivityRepository
                .findByDeviceIdAndStartedAtBetweenOrderByStartedAtAsc(deviceId, range[0], range[1]);
        List<BrowsingActivityDto> content = all.stream()
                .skip(pageable.getOffset())
                .limit(pageable.getPageSize())
                .map(DeviceActivityController::toDto)
                .toList();
        return new PageImpl<>(content, pageable, all.size());
    }

    private Instant[] resolveRange(LocalDate from, LocalDate to) {
        LocalDate start = from == null ? LocalDate.now(ZoneOffset.UTC).minusDays(1) : from;
        LocalDate end = to == null ? LocalDate.now(ZoneOffset.UTC).plusDays(1) : to.plusDays(1);
        return new Instant[]{
                start.atStartOfDay(ZoneOffset.UTC).toInstant(),
                end.atStartOfDay(ZoneOffset.UTC).toInstant()
        };
    }

    private static UsageSessionDto toDto(UsageSession session) {
        return new UsageSessionDto(session.getId(), session.getDeviceId(), session.getProcessName(),
                session.getWindowTitle(), session.getStartedAt(), session.getEndedAt(),
                session.getDurationSeconds(), session.isIdle(), session.isForeground());
    }

    private static BrowsingActivityDto toDto(BrowsingActivity activity) {
        return new BrowsingActivityDto(activity.getId(), activity.getDeviceId(), activity.getUrl(),
                activity.getDomain(), activity.getBrowserName(), activity.getTabTitle(),
                activity.getStartedAt(), activity.getEndedAt(), activity.getDurationSeconds(),
                activity.isIdle());
    }
}