package com.managementcontrol.web.auth;

import com.managementcontrol.common.NotFoundException;
import com.managementcontrol.config.ManagementControlProperties;
import com.managementcontrol.domain.AdminUser;
import com.managementcontrol.domain.Role;
import com.managementcontrol.repository.AdminUserRepository;
import com.managementcontrol.security.AdminPrincipal;
import com.managementcontrol.security.JwtService;
import com.managementcontrol.web.admin.dto.LoginRequest;
import com.managementcontrol.web.admin.dto.LoginResponse;
import jakarta.validation.Valid;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

/** Вхід адміністратора в панель керування. */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final AdminUserRepository adminUserRepository;
    private final ManagementControlProperties properties;

    public AuthController(AuthenticationManager authenticationManager,
                          JwtService jwtService,
                          AdminUserRepository adminUserRepository,
                          ManagementControlProperties properties) {
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.adminUserRepository = adminUserRepository;
        this.properties = properties;
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.username(), request.password()));

        AdminPrincipal principal = (AdminPrincipal) authentication.getPrincipal();
        String token = jwtService.issue(principal);

        adminUserRepository.findByUsername(principal.getUsername()).ifPresent(user -> {
            user.setLastLoginAt(Instant.now());
            adminUserRepository.save(user);
        });

        Instant expiresAt = Instant.now().plus(properties.getJwt().getAccessTokenTtl());
        return new LoginResponse(token, "Bearer", expiresAt, principal.getUsername(),
                principal.getFullName(), principal.getRole().name());
    }

    @GetMapping("/me")
    public LoginResponse me() {
        AdminPrincipal principal = (AdminPrincipal) SecurityContextHolder.getContext()
                .getAuthentication().getPrincipal();
        AdminUser user = adminUserRepository.findByUsername(principal.getUsername())
                .orElseThrow(() -> new NotFoundException("Користувача не знайдено"));
        return new LoginResponse(null, "Bearer", null, user.getUsername(), user.getFullName(),
                user.getRole() == null ? Role.VIEWER.name() : user.getRole().name());
    }
}
