package com.managementcontrol.service;

import com.managementcontrol.domain.Device;
import com.managementcontrol.domain.DeviceStatus;
import com.managementcontrol.repository.BrowsingActivityRepository;
import com.managementcontrol.repository.LocationPingRepository;
import com.managementcontrol.repository.ScreenshotRepository;
import com.managementcontrol.repository.UsageSessionRepository;
import com.managementcontrol.web.admin.dto.DailyReport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReportServiceTest {

        @Mock
        private UsageSessionRepository usageSessionRepository;

        @Mock
        private BrowsingActivityRepository browsingActivityRepository;

        @Mock
        private ScreenshotRepository screenshotRepository;

        @Mock
        private LocationPingRepository locationPingRepository;

        @InjectMocks
        private ReportService reportService;

        @Test
        void формує_звіт_за_день() {
                Device device = new Device();
                device.setId(7L);
                device.setDeviceId("dev-7");
                device.setHostname("work-pc");
                device.setStatus(DeviceStatus.ACTIVE);

                LocalDate date = LocalDate.of(2026, 10, 22);
                Instant from = date.atStartOfDay(ZoneOffset.UTC).toInstant();
                Instant to = date.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();

                when(usageSessionRepository.sumActiveSeconds(eq(7L), any(Instant.class), any(Instant.class)))
                                .thenReturn(3600L);
                when(screenshotRepository.countForDay(eq(7L), any(Instant.class), any(Instant.class)))
                                .thenReturn(12L);
                when(usageSessionRepository.topProcesses(eq(7L), any(Instant.class), any(Instant.class)))
                                .thenReturn(List.<Object[]>of(new Object[] { "chrome.exe", 1800L }));
                when(browsingActivityRepository.topDomains(eq(7L), any(Instant.class), any(Instant.class)))
                                .thenReturn(List.<Object[]>of(new Object[] { "example.com", 600L }));

                DailyReport report = reportService.dailyReport(device, date);

                assertThat(report.activeSeconds()).isEqualTo(3600L);
                assertThat(report.screenshotCount()).isEqualTo(12L);
                assertThat(report.topApps()).hasSize(1);
                assertThat(report.topApps().get(0).name()).isEqualTo("chrome.exe");
                assertThat(report.topSites().get(0).name()).isEqualTo("example.com");
                assertThat(report.date()).isEqualTo(date);
        }

        @Test
        void формує_звіт_коли_даних_за_день_немає() {
                Device device = new Device();
                device.setId(9L);
                device.setDeviceId("dev-9");
                device.setHostname("idle-pc");
                device.setStatus(DeviceStatus.ACTIVE);

                LocalDate date = LocalDate.of(2026, 10, 23);

                when(usageSessionRepository.sumActiveSeconds(eq(9L), any(Instant.class), any(Instant.class)))
                                .thenReturn(0L);
                when(screenshotRepository.countForDay(eq(9L), any(Instant.class), any(Instant.class)))
                                .thenReturn(0L);
                when(usageSessionRepository.topProcesses(eq(9L), any(Instant.class), any(Instant.class)))
                                .thenReturn(List.of());
                when(browsingActivityRepository.topDomains(eq(9L), any(Instant.class), any(Instant.class)))
                                .thenReturn(List.of());

                DailyReport report = reportService.dailyReport(device, date);

                assertThat(report.activeSeconds()).isEqualTo(0L);
                assertThat(report.screenshotCount()).isEqualTo(0L);
                assertThat(report.topApps()).isEmpty();
                assertThat(report.topSites()).isEmpty();
                assertThat(report.date()).isEqualTo(date);
        }
}