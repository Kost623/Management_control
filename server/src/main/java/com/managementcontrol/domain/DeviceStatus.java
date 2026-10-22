package com.managementcontrol.domain;

public enum DeviceStatus {

    /** Пристрій зареєстровано, агент працює штатно. */
    ACTIVE,

    /** Пристрій заблоковано адміністратором (remote lock). */
    LOCKED,

    /** Агент не виходить на зв'язок більше очікуваного часу. */
    OFFLINE,

    /** Пристрій заблоковано адміністратором назавжди. */
    BLOCKED
}
