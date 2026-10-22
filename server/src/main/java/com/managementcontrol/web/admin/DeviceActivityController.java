package com.managementcontrol.web.admin;

import com.managementcontrol.domain.BrowsingActivity;
import com.managementcontrol.domain.UsageSession;
import com.managementcontrol.repository.BrowsingActivityRepository;
import com.managementcontrol.repository.UsageSessionRepository;
import com.managementcontrol.web.admin.dto.BrowsingActivityDto;
import com.managementcontrol.web.admin.dto.UsageSessionDto;
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
public class DeviceActivityController {

    private final UsageSessionRepository usageSessionRepository;
    private final BrowsingActivityRepository browsingActivityRepository;

    public DeviceActivityController(UsageSessionRepository usageSessionRepository,
                                    BrowsingActivityRepository browsingActivityRepository) {
        this.usageSessionRepository = usageSessionRepository;
        this.browsingActivityRepository = browsingActivityRepository;
    }

    @GetMapping("/{deviceId}/usage-sessions")
    public Page<UsageSessionDto> usageSessions(
            @PathVariable Long deviceId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") int page,
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

    @GetMapping("/{deviceId}/browsing")
    public Page<BrowsingActivityDto> browsing(
            @PathVariable Long deviceId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") int page,
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
