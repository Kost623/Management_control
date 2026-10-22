package com.managementcontrol.config;

import com.managementcontrol.domain.AdminUser;
import com.managementcontrol.domain.Role;
import com.managementcontrol.repository.AdminUserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Створює початкового адміністратора, якщо його ще немає.
 * Логін і пароль беруться з MC_ADMIN_USERNAME / MC_ADMIN_PASSWORD.
 */
@Component
public class AdminBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

    private final AdminUserRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final ManagementControlProperties properties;

    public AdminBootstrap(AdminUserRepository repository,
                          PasswordEncoder passwordEncoder,
                          ManagementControlProperties properties) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
        this.properties = properties;
    }

    @Override
    @Transactional
    public void run(org.springframework.boot.ApplicationArguments args) {
        String username = properties.getAdmin().getUsername();
        if (username == null || username.isBlank() || repository.existsByUsername(username)) {
            return;
        }
        AdminUser admin = new AdminUser();
        admin.setUsername(username);
        admin.setPasswordHash(passwordEncoder.encode(properties.getAdmin().getPassword()));
        admin.setFullName("Адміністратор");
        admin.setRole(Role.ADMIN);
        admin.setEnabled(true);
        admin.setCreatedAt(Instant.now());
        repository.save(admin);
        log.info("Створено початкового адміністратора '{}'. Змініть пароль у production!", username);
    }
}
