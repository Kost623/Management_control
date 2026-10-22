package com.managementcontrol.security;

import com.managementcontrol.domain.Device;
import com.managementcontrol.domain.DeviceStatus;
import com.managementcontrol.repository.DeviceRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.Optional;

/**
 * Автентифікація запитів за Bearer-токеном.
 * Токени пристроїв мають префікс {@code mc_dev_}, адміністратори — JWT.
 */
@Component
public class ApiAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(ApiAuthenticationFilter.class);

    private final JwtService jwtService;
    private final AdminUserDetailsService adminUserDetailsService;
    private final DeviceRepository deviceRepository;

    public ApiAuthenticationFilter(JwtService jwtService,
                                   AdminUserDetailsService adminUserDetailsService,
                                   DeviceRepository deviceRepository) {
        this.jwtService = jwtService;
        this.adminUserDetailsService = adminUserDetailsService;
        this.deviceRepository = deviceRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring("Bearer ".length()).trim();
            Authentication authentication = authenticate(token);
            if (authentication != null) {
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        }
        chain.doFilter(request, response);
    }

    private Authentication authenticate(String token) {
        if (TokenService.isDeviceToken(token)) {
            return authenticateDevice(token);
        }
        return authenticateAdmin(token);
    }

    private Authentication authenticateDevice(String token) {
        Optional<Device> found = deviceRepository.findByTokenHash(TokenService.hash(token));
        if (found.isEmpty()) {
            log.debug("Невідомий токен пристрою");
            return null;
        }
        Device device = found.get();
        if (device.getTokenExpiresAt() != null && device.getTokenExpiresAt().isBefore(Instant.now())) {
            log.debug("Токен пристрою {} протермінований", device.getDeviceId());
            return null;
        }
        if (device.getStatus() == DeviceStatus.BLOCKED) {
            log.debug("Пристрій {} заблокований адміністратором", device.getDeviceId());
            return null;
        }
        DevicePrincipal principal = DevicePrincipal.of(device);
        return new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
    }

    private Authentication authenticateAdmin(String token) {
        try {
            var claims = jwtService.parse(token);
            String username = claims.getSubject();
            if (username == null) {
                return null;
            }
            var userDetails = adminUserDetailsService.loadUserByUsername(username);
            if (userDetails instanceof AdminPrincipal principal) {
                return new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
            }
            return null;
        } catch (Exception ex) {
            log.debug("Невалідний admin-токен: {}", ex.getMessage());
            return null;
        }
    }
}
