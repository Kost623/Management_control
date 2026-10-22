package com.managementcontrol.web.admin;

import com.managementcontrol.domain.DeviceStatus;
import com.managementcontrol.service.DeviceService;
import com.managementcontrol.web.admin.dto.DeviceDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;

/** Керування пристроями: перегляд, блокування, розблокування. */
@RestController
@RequestMapping("/api/v1/admin/devices")
public class DeviceAdminController {

    private final DeviceService deviceService;

    public DeviceAdminController(DeviceService deviceService) {
        this.deviceService = deviceService;
    }

    @GetMapping
    public Page<DeviceDto> list(@RequestParam(required = false) DeviceStatus status,
                                @RequestParam(defaultValue = "0") int page,
                                @RequestParam(defaultValue = "50") int size) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 200),
                Sort.by(Sort.Direction.DESC, "id"));
        return deviceService.list(status, pageable).map(DeviceDto::from);
    }

    @GetMapping("/{id}")
    public DeviceDto get(@PathVariable Long id) {
        return DeviceDto.from(deviceService.get(id));
    }

    /** Заблокувати пристрій (екран + робочу сесію). */
    @PostMapping("/{id}/lock")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<DeviceDto> lock(@PathVariable Long id,
                                          @RequestParam(required = false) String reason,
                                          Principal principal) {
        return ResponseEntity.ok(DeviceDto.from(deviceService.lock(id, reason, principal.getName())));
    }

    /** Зняти блокування. */
    @PostMapping("/{id}/unlock")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<DeviceDto> unlock(@PathVariable Long id, Principal principal) {
        return ResponseEntity.ok(DeviceDto.from(deviceService.unlock(id, principal.getName())));
    }

    /** Заблокувати пристрій назавжди — агент більше не зможе автентифікуватися. */
    @PostMapping("/{id}/block")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<DeviceDto> block(@PathVariable Long id,
                                           @RequestParam(required = false) String reason,
                                           Principal principal) {
        return ResponseEntity.ok(DeviceDto.from(deviceService.block(id, reason, principal.getName())));
    }
}
