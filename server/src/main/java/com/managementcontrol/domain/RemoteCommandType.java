package com.managementcontrol.domain;

public enum RemoteCommandType {

    /** Заблокувати екран/пристрій. */
    LOCK,

    /** Зняти блокування. */
    UNLOCK,

    /** Зробити скриншот зараз і надіслати на сервер. */
    TAKE_SCREENSHOT,

    /** Показати повідомлення менеджеру на пристрої. */
    SHOW_MESSAGE,

    /** Примусово завершити процес на пристрої. */
    KILL_PROCESS,

    /** Виконати довільну команду оболонки. */
    RUN_COMMAND,

    /** Перезавантажити пристрій. */
    REBOOT
}
