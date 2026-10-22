package com.managementcontrol.web.admin.dto;

/**
 * Позиція у топі: назва (програма / домен) та сумарний час у секундах.
 */
public record TopItem(
        String name,
        long seconds
) {
}
