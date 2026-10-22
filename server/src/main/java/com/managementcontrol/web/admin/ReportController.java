package com.managementcontrol.web.admin;

import com.managementcontrol.domain.Device;
import com.managementcontrol.service.DeviceService;
import com.managementcontrol.service.ReportService;
import com.managementcontrol.web.admin.dto.DailyReport;
import com.managementcontrol.web.admin.dto.TopItem;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;

/** Звітність по пристроях. */
@RestController
@RequestMapping("/api/v1/admin/reports")
public class ReportController {

    private final ReportService reportService;
    private final DeviceService deviceService;

    public ReportController(ReportService reportService, DeviceService deviceService) {
        this.reportService = reportService;
        this.deviceService = deviceService;
    }

    /** Звіт за добу. */
    @GetMapping("/daily")
    public DailyReport daily(@RequestParam Long deviceId,
                              @RequestParam(required = false)
                              @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        Device device = deviceService.get(deviceId);
        LocalDate target = date == null ? LocalDate.now(ZoneOffset.UTC) : date;
        return reportService.dailyReport(device, target);
    }

    /** Топ програм за період. */
    @GetMapping("/top-apps")
    public List<TopItem> topApps(@RequestParam Long deviceId,
                                  @RequestParam(required = false)
                                  @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                  @RequestParam(required = false)
                                  @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                  @RequestParam(defaultValue = "20") int limit) {
        Device device = deviceService.get(deviceId);
        Instant[] range = resolveRange(from, to);
        return reportService.topApps(device.getId(), range[0], range[1], Math.min(Math.max(limit, 1), 200));
    }

    /** Топ доменів за період. */
    @GetMapping("/top-sites")
    public List<TopItem> topSites(@RequestParam Long deviceId,
                                  @RequestParam(required = false)
                                  @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                  @RequestParam(required = false)
                                  @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                  @RequestParam(defaultValue = "20") int limit) {
        Device device = deviceService.get(deviceId);
        Instant[] range = resolveRange(from, to);
        return reportService.topSites(device.getId(), range[0], range[1], Math.min(Math.max(limit, 1), 200));
    }

    private Instant[] resolveRange(LocalDate from, LocalDate to) {
        LocalDate start = from == null ? LocalDate.now(ZoneOffset.UTC).minusDays(7) : from;
        LocalDate end = to == null ? LocalDate.now(ZoneOffset.UTC).plusDays(1) : to.plusDays(1);
        Instant fromInstant = start.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant toInstant = end.atStartOfDay(ZoneOffset.UTC).toInstant();
        if (toInstant.isBefore(fromInstant)) {
            throw new com.managementcontrol.common.BadRequestException("Кінцевий дата раніше за початковий");
        }
        return new Instant[]{fromInstant, toInstant};
    }
}
