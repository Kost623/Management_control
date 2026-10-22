package com.managementcontrol.domain;

public enum RemoteCommandStatus {

    /** Команда створена, ще не підтверджено отримання агентом. */
    PENDING,

    /** Агент отримав команду. */
    DELIVERED,

    /** Агент виконав команду. */
    EXECUTED,

    /** Виконання завершилося помилкою. */
    FAILED,

    /** Термін дії минув. */
    EXPIRED
}
