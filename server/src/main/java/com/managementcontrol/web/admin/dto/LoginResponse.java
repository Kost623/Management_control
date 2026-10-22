package com.managementcontrol.web.admin.dto;

import java.time.Instant;

/** Відповідь на вхід: JWT + профіль користувача. */
public record LoginResponse(
        String token,
        String tokenType,
        Instant expiresAt,
        String username,
        String fullName,
        String role
) {
}
