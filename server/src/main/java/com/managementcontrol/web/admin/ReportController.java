package com.managementcontrol.web.admin;

import com.managementcontrol.domain.Device;
import com.managementcontrol.service.DeviceService;
import com.managementcontrol.service.ReportService;
import com.managementcontrol.web.admin.dto.DailyReport;
import com.managementcontrol.web.admin.dto.TopItem;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Admin: Reports", description = "Агреговані звіти по активності пристроїв")
@SecurityRequirement(name = "bearerAuth")
public class ReportController {

    private final ReportService reportService;
    private final DeviceService deviceService;

    public ReportController(ReportService reportService, DeviceService deviceService) {
        this.reportService = reportService;
        this.deviceService = deviceService;
    }

    @Operation(
            summary = "Звіт за добу",
            description = "Повертає сумарну активність пристрою за одну добу: загальний активний час, " +
                    "кількість скриншотів, топ програм і топ сайтів. Якщо `date` не вказано — поточна дата (UTC)."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Звіт за добу"),
            @ApiResponse(responseCode = "401", description = "Відсутній або недійсний JWT"),
            @ApiResponse(responseCode = "404", description = "Пристрій з таким ID не знайдено")
    })
    @GetMapping("/daily")
    public DailyReport daily(
            @Parameter(description = "Внутрішній ID пристрою", required = true)
            @RequestParam Long deviceId,
            @Parameter(description = "Дата звіту (YYYY-MM-DD). За замовчуванням — поточна дата UTC")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        Device device = deviceService.get(deviceId);
        LocalDate target = date == null ? LocalDate.now(ZoneOffset.UTC) : date;
        return reportService.dailyReport(device, target);
    }

    @Operation(
            summary = "Топ програм за період",
            description = "Повертає найбільш використовувані програми, відсортовані за сумарною тривалістю. " +
                    "Період за замовчуванням — останні 7 днів."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Список топ-програм"),
            @ApiResponse(responseCode = "400", description = "Кінцева дата раніша за початкову"),
            @ApiResponse(responseCode = "401", description = "Відсутній або недійсний JWT"),
            @ApiResponse(responseCode = "404", description = "Пристрій з таким ID не знайдено")
    })
    @GetMapping("/top-apps")
    public List<TopItem> topApps(
            @Parameter(description = "Внутрішній ID пристрою", required = true)
            @RequestParam Long deviceId,
            @Parameter(description = "Початок періоду (YYYY-MM-DD). За замовчуванням — 7 днів тому")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @Parameter(description = "Кінець періоду (YYYY-MM-DD). За замовчуванням — завтра")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @Parameter(description = "Максимальна кількість елементів у відповіді (1–200)")
            @RequestParam(defaultValue = "20") int limit) {
        Device device = deviceService.get(deviceId);
        Instant[] range = resolveRange(from, to);
        return reportService.topApps(device.getId(), range[0], range[1], Math.min(Math.max(limit, 1), 200));
    }

    @Operation(
            summary = "Топ доменів за період",
            description = "Повертає найбільш відвідувані домени, відсортовані за сумарною тривалістю. " +
                    "Період за замовчуванням — останні 7 днів."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Список топ-доменів"),
            @ApiResponse(responseCode = "400", description = "Кінцева дата раніша за початкову"),
            @ApiResponse(responseCode = "401", description = "Відсутній або недійсний JWT"),
            @ApiResponse(responseCode = "404", description = "Пристрій з таким ID не знайдено")
    })
    @GetMapping("/top-sites")
    public List<TopItem> topSites(
            @Parameter(description = "Внутрішній ID пристрою", required = true)
            @RequestParam Long deviceId,
            @Parameter(description = "Початок періоду (YYYY-MM-DD). За замовчуванням — 7 днів тому")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @Parameter(description = "Кінець періоду (YYYY-MM-DD). За замовчуванням — завтра")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @Parameter(description = "Максимальна кількість елементів у відповіді (1–200)")
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