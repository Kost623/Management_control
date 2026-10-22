-- Management Control: початкова схема БД (MariaDB)

CREATE TABLE devices (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    device_id         VARCHAR(128) NOT NULL,
    hostname          VARCHAR(255) NOT NULL,
    os_name           VARCHAR(128) NULL,
    os_version        VARCHAR(128) NULL,
    agent_version     VARCHAR(64)  NULL,
    mac_address       VARCHAR(64)  NULL,
    ip_address        VARCHAR(64)  NULL,
    os_user           VARCHAR(128) NULL,
    assigned_manager  VARCHAR(128) NULL,
    status            VARCHAR(32)  NOT NULL,
    enrolled_at       DATETIME(6)  NOT NULL,
    last_seen_at      DATETIME(6)  NULL,
    token_hash        VARCHAR(128) NOT NULL,
    token_expires_at  DATETIME(6)  NULL,
    tamper_protection BOOLEAN      NOT NULL DEFAULT TRUE,
    lock_reason       VARCHAR(512) NULL,
    locked_at         DATETIME(6)  NULL,
    notes             VARCHAR(1024) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_devices_device_id (device_id),
    KEY idx_devices_status (status),
    KEY idx_devices_last_seen (last_seen_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE usage_sessions (
    id               BIGINT      NOT NULL AUTO_INCREMENT,
    device_id        BIGINT      NOT NULL,
    process_name     VARCHAR(255) NOT NULL,
    window_title     VARCHAR(512) NULL,
    exe_path         VARCHAR(1024) NULL,
    started_at       DATETIME(6) NOT NULL,
    ended_at         DATETIME(6) NOT NULL,
    duration_seconds BIGINT      NOT NULL DEFAULT 0,
    idle             BOOLEAN     NOT NULL DEFAULT FALSE,
    foreground       BOOLEAN     NOT NULL DEFAULT FALSE,
    PRIMARY KEY (id),
    KEY idx_usage_device_time (device_id, started_at),
    KEY idx_usage_process (process_name),
    CONSTRAINT fk_usage_device FOREIGN KEY (device_id) REFERENCES devices (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE browsing_activities (
    id               BIGINT      NOT NULL AUTO_INCREMENT,
    device_id        BIGINT      NOT NULL,
    url              VARCHAR(2048) NULL,
    domain           VARCHAR(255) NULL,
    browser_name     VARCHAR(128) NULL,
    tab_title        VARCHAR(512) NULL,
    started_at       DATETIME(6) NOT NULL,
    ended_at         DATETIME(6) NOT NULL,
    duration_seconds BIGINT      NOT NULL DEFAULT 0,
    idle             BOOLEAN     NOT NULL DEFAULT FALSE,
    PRIMARY KEY (id),
    KEY idx_browsing_device_time (device_id, started_at),
    KEY idx_browsing_domain (domain),
    CONSTRAINT fk_browsing_device FOREIGN KEY (device_id) REFERENCES devices (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE process_events (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    device_id    BIGINT       NOT NULL,
    process_name VARCHAR(255) NOT NULL,
    exe_path     VARCHAR(1024) NULL,
    command_line VARCHAR(2048) NULL,
    occurred_at  DATETIME(6)  NOT NULL,
    action       VARCHAR(32)  NOT NULL,
    PRIMARY KEY (id),
    KEY idx_process_device_time (device_id, occurred_at),
    KEY idx_process_name (process_name),
    CONSTRAINT fk_process_device FOREIGN KEY (device_id) REFERENCES devices (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE screenshots (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    device_id    BIGINT       NOT NULL,
    file_name    VARCHAR(255) NOT NULL,
    storage_path VARCHAR(1024) NOT NULL,
    size_bytes   BIGINT       NOT NULL DEFAULT 0,
    sha256       VARCHAR(64)  NULL,
    taken_at     DATETIME(6)  NOT NULL,
    trigger_type VARCHAR(32)  NOT NULL,
    width        INT          NULL,
    height       INT          NULL,
    note         VARCHAR(512) NULL,
    PRIMARY KEY (id),
    KEY idx_screenshot_device_time (device_id, taken_at),
    CONSTRAINT fk_screenshot_device FOREIGN KEY (device_id) REFERENCES devices (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE location_pings (
    id          BIGINT      NOT NULL AUTO_INCREMENT,
    device_id   BIGINT      NOT NULL,
    latitude    DOUBLE      NOT NULL,
    longitude   DOUBLE      NOT NULL,
    accuracy    DOUBLE      NULL,
    altitude    DOUBLE      NULL,
    speed       DOUBLE      NULL,
    captured_at DATETIME(6) NOT NULL,
    source      VARCHAR(32) NULL,
    PRIMARY KEY (id),
    KEY idx_location_device_time (device_id, captured_at),
    CONSTRAINT fk_location_device FOREIGN KEY (device_id) REFERENCES devices (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE remote_commands (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    device_id    BIGINT       NOT NULL,
    type         VARCHAR(32)  NOT NULL,
    status       VARCHAR(32)  NOT NULL,
    payload      VARCHAR(4096) NULL,
    created_at   DATETIME(6)  NOT NULL,
    delivered_at DATETIME(6)  NULL,
    executed_at  DATETIME(6)  NULL,
    expires_at   DATETIME(6)  NULL,
    created_by   VARCHAR(128) NULL,
    result       VARCHAR(1024) NULL,
    PRIMARY KEY (id),
    KEY idx_command_device_created (device_id, created_at),
    KEY idx_command_status (status),
    CONSTRAINT fk_command_device FOREIGN KEY (device_id) REFERENCES devices (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE enrollment_codes (
    id                BIGINT      NOT NULL AUTO_INCREMENT,
    code              VARCHAR(32) NOT NULL,
    label             VARCHAR(128) NULL,
    assigned_to       VARCHAR(128) NULL,
    bound_device_id   VARCHAR(128) NULL,
    created_at        DATETIME(6) NOT NULL,
    expires_at        DATETIME(6) NOT NULL,
    used_at           DATETIME(6) NULL,
    used_by_device_id VARCHAR(128) NULL,
    active            BOOLEAN     NOT NULL DEFAULT TRUE,
    PRIMARY KEY (id),
    UNIQUE KEY uk_enrollment_code (code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE admin_users (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    username      VARCHAR(64)  NOT NULL,
    password_hash VARCHAR(128) NOT NULL,
    full_name     VARCHAR(128) NULL,
    role          VARCHAR(32)  NOT NULL,
    enabled       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    DATETIME(6)  NOT NULL,
    last_login_at DATETIME(6)  NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_admin_username (username)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE audit_logs (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    actor       VARCHAR(128) NOT NULL,
    action      VARCHAR(64)  NOT NULL,
    target_type VARCHAR(64)  NULL,
    target_id   VARCHAR(128) NULL,
    details     VARCHAR(2048) NULL,
    ip_address  VARCHAR(64)  NULL,
    created_at  DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    KEY idx_audit_created (created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
