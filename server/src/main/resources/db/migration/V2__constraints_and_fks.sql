-- Доповнення схеми: зовнішні ключі та обмеження, яких бракувало у V1.

-- Зв'язок enrollment_codes -> devices через бізнес-ключ device_id (UNIQUE в devices)
ALTER TABLE enrollment_codes
    ADD CONSTRAINT fk_enrollment_bound_device
        FOREIGN KEY (bound_device_id) REFERENCES devices (device_id) ON DELETE SET NULL,
    ADD CONSTRAINT fk_enrollment_used_device
        FOREIGN KEY (used_by_device_id) REFERENCES devices (device_id) ON DELETE SET NULL;

-- Формалізація зв'язку "пристрій закріплений за адміністратором" (N:1)
ALTER TABLE devices
    ADD COLUMN assigned_manager_id BIGINT NULL AFTER assigned_manager,
    ADD CONSTRAINT fk_device_manager
        FOREIGN KEY (assigned_manager_id) REFERENCES admin_users (id) ON DELETE SET NULL;

-- CHECK на enum-подібні поля (значення відповідають Java enum-ам)
ALTER TABLE devices
    ADD CONSTRAINT chk_devices_status
        CHECK (status IN ('ACTIVE', 'LOCKED', 'OFFLINE', 'BLOCKED'));

ALTER TABLE admin_users
    ADD CONSTRAINT chk_admin_role
        CHECK (role IN ('ADMIN', 'VIEWER'));

ALTER TABLE remote_commands
    ADD CONSTRAINT chk_command_status
        CHECK (status IN ('PENDING', 'DELIVERED', 'EXECUTED', 'FAILED', 'EXPIRED')),
    ADD CONSTRAINT chk_command_type
        CHECK (type IN ('LOCK', 'UNLOCK', 'TAKE_SCREENSHOT', 'SHOW_MESSAGE',
                         'KILL_PROCESS', 'RUN_COMMAND', 'REBOOT'));

-- Географічні межі координат
ALTER TABLE location_pings
    ADD CONSTRAINT chk_location_lat CHECK (latitude BETWEEN -90 AND 90),
    ADD CONSTRAINT chk_location_lng CHECK (longitude BETWEEN -180 AND 180);

-- Невід'ємні тривалості/розміри
ALTER TABLE usage_sessions
    ADD CONSTRAINT chk_usage_duration CHECK (duration_seconds >= 0);

ALTER TABLE browsing_activities
    ADD CONSTRAINT chk_browsing_duration CHECK (duration_seconds >= 0);

ALTER TABLE screenshots
    ADD CONSTRAINT chk_screenshot_size CHECK (size_bytes >= 0);

-- CHECK для process_events.action (значення задає агент: agent/src/activity.cpp)
ALTER TABLE process_events
    ADD CONSTRAINT chk_process_action
        CHECK (action IN ('STARTED', 'EXITED'));

-- CHECK для screenshots.trigger_type (значення задає агент: agent/src/agent.cpp)
ALTER TABLE screenshots
    ADD CONSTRAINT chk_screenshot_trigger
        CHECK (trigger_type IN ('AUTO_RANDOM', 'COMMAND'));
