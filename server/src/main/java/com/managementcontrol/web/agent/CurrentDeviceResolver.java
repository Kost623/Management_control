package com.managementcontrol.web.agent;

import com.managementcontrol.domain.Device;
import com.managementcontrol.repository.DeviceRepository;
import com.managementcontrol.security.DevicePrincipal;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import com.managementcontrol.common.NotFoundException;

/** Доступ до поточного пристрою, автентифікованого агентом. */
@Component
public class CurrentDeviceResolver {

    private final DeviceRepository deviceRepository;

    public CurrentDeviceResolver(DeviceRepository deviceRepository) {
        this.deviceRepository = deviceRepository;
    }

    public Device require() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof DevicePrincipal principal)) {
            throw new NotFoundException("Пристрій не автентифіковано");
        }
        return deviceRepository.findById(principal.getId())
                .orElseThrow(() -> new NotFoundException("Device", principal.getId()));
    }
}
