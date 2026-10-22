package com.managementcontrol.service;

import com.managementcontrol.domain.Device;
import com.managementcontrol.domain.LocationPing;
import com.managementcontrol.repository.BrowsingActivityRepository;
import com.managementcontrol.repository.LocationPingRepository;
import com.managementcontrol.repository.ScreenshotRepository;
import com.managementcontrol.repository.UsageSessionRepository;
import com.managementcontrol.web.admin.dto.DailyReport;
import com.managementcontrol.web.admin.dto.TopItem;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

/** Звітність по пристрою за добу / період. */
@Service
public class ReportService {

    private final UsageSessionRepository usageSessionRepository;
    private final BrowsingActivityRepository browsingActivityRepository;
    private final ScreenshotRepository screenshotRepository;
    private final LocationPingRepository locationPingRepository;

    public ReportService(UsageSessionRepository usageSessionRepository,
                         BrowsingActivityRepository browsingActivityRepository,
                         ScreenshotRepository screenshotRepository,
                         LocationPingRepository locationPingRepository) {
        this.usageSessionRepository = usageSessionRepository;
        this.browsingActivityRepository = browsingActivityRepository;
        this.screenshotRepository = screenshotRepository;
        this.locationPingRepository = locationPingRepository;
    }

    @Transactional(readOnly = true)
    public DailyReport dailyReport(Device device, LocalDate date) {
        Instant from = date.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant to = date.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();

        long activeSeconds = usageSessionRepository.sumActiveSeconds(device.getId(), from, to);
        long screenshots = screenshotRepository.countForDay(device.getId(), from, to);
        List<TopItem> topApps = topApps(device.getId(), from, to, 10);
        List<TopItem> topSites = topSites(device.getId(), from, to, 10);
        LocationPing lastLocation = locationPingRepository
                .findFirstByDeviceIdOrderByCapturedAtDesc(device.getId())
                .orElse(null);

        return new DailyReport(
                device.getId(),
                device.getDeviceId(),
                device.getHostname(),
                date,
                activeSeconds,
                screenshots,
                topApps,
                topSites,
                lastLocation == null ? null : lastLocation.getLatitude(),
                lastLocation == null ? null : lastLocation.getLongitude(),
                lastLocation == null ? null : lastLocation.getCapturedAt());
    }

    @Transactional(readOnly = true)
    public List<TopItem> topApps(Long deviceId, Instant from, Instant to, int limit) {
        return usageSessionRepository.topProcesses(deviceId, from, to).stream()
                .limit(limit)
                .map(row -> new TopItem((String) row[0], ((Number) row[1]).longValue()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<TopItem> topSites(Long deviceId, Instant from, Instant to, int limit) {
        return browsingActivityRepository.topDomains(deviceId, from, to).stream()
                .limit(limit)
                .map(row -> new TopItem((String) row[0], ((Number) row[1]).longValue()))
                .toList();
    }
}
