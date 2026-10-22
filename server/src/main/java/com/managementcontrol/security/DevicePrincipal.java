package com.managementcontrol.security;

import com.managementcontrol.domain.Device;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

/** Автентифікований пристрій (агент). */
public class DevicePrincipal implements UserDetails {

    private final Long id;
    private final String deviceId;
    private final String hostname;

    public DevicePrincipal(Long id, String deviceId, String hostname) {
        this.id = id;
        this.deviceId = deviceId;
        this.hostname = hostname;
    }

    public static DevicePrincipal of(Device device) {
        return new DevicePrincipal(device.getId(), device.getDeviceId(), device.getHostname());
    }

    public Long getId() {
        return id;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public String getHostname() {
        return hostname;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_DEVICE"), new SimpleGrantedAuthority("DEVICE"));
    }

    @Override
    public String getPassword() {
        return null;
    }

    @Override
    public String getUsername() {
        return deviceId;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }
}
