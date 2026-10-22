package com.managementcontrol.security;

import com.managementcontrol.domain.AdminUser;
import com.managementcontrol.domain.Role;
import com.managementcontrol.repository.AdminUserRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminUserDetailsService implements UserDetailsService {

    private final AdminUserRepository repository;

    public AdminUserDetailsService(AdminUserRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        AdminUser user = repository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("Користувача " + username + " не знайдено"));
        return new AdminPrincipal(user.getId(), user.getUsername(), user.getPasswordHash(),
                user.getFullName(), user.getRole() == null ? Role.VIEWER : user.getRole());
    }
}
