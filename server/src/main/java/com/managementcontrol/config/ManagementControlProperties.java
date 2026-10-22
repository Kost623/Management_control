package com.managementcontrol.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Налаштування сервера Management Control.
 * Значення перевизначаються через змінні оточення (див. application.yml).
 */
@ConfigurationProperties(prefix = "management.control")
public class ManagementControlProperties {

    /** Термін дії токена пристрою. */
    private Duration deviceTokenTtl = Duration.ofDays(30);

    /** Скільки агент чекає на команди під час long-poll. */
    private Duration commandPollTimeout = Duration.ofSeconds(30);

    /** Очікуваний інтервал heartbeat від агента (для визначення OFFLINE). */
    private Duration heartbeatInterval = Duration.ofSeconds(60);

    /** Багаторазовий інтервал heartbeat, після якого пристрій вважається офлайновим. */
    private Duration offlineThreshold = Duration.ofMinutes(10);

    private Jwt jwt = new Jwt();
    private Admin admin = new Admin();
    private Storage storage = new Storage();

    public Duration getDeviceTokenTtl() {
        return deviceTokenTtl;
    }

    public void setDeviceTokenTtl(Duration deviceTokenTtl) {
        this.deviceTokenTtl = deviceTokenTtl;
    }

    public Duration getCommandPollTimeout() {
        return commandPollTimeout;
    }

    public void setCommandPollTimeout(Duration commandPollTimeout) {
        this.commandPollTimeout = commandPollTimeout;
    }

    public Duration getHeartbeatInterval() {
        return heartbeatInterval;
    }

    public void setHeartbeatInterval(Duration heartbeatInterval) {
        this.heartbeatInterval = heartbeatInterval;
    }

    public Duration getOfflineThreshold() {
        return offlineThreshold;
    }

    public void setOfflineThreshold(Duration offlineThreshold) {
        this.offlineThreshold = offlineThreshold;
    }

    public Jwt getJwt() {
        return jwt;
    }

    public void setJwt(Jwt jwt) {
        this.jwt = jwt;
    }

    public Admin getAdmin() {
        return admin;
    }

    public void setAdmin(Admin admin) {
        this.admin = admin;
    }

    public Storage getStorage() {
        return storage;
    }

    public void setStorage(Storage storage) {
        this.storage = storage;
    }

    public static class Jwt {

        /** Секрет для підпису JWT (має бути щонайменше 32 байти). */
        private String secret = "change-me-change-me-change-me-change-me-32b";

        private Duration accessTokenTtl = Duration.ofHours(8);

        private String issuer = "management-control";

        public String getSecret() {
            return secret;
        }

        public void setSecret(String secret) {
            this.secret = secret;
        }

        public Duration getAccessTokenTtl() {
            return accessTokenTtl;
        }

        public void setAccessTokenTtl(Duration accessTokenTtl) {
            this.accessTokenTtl = accessTokenTtl;
        }

        public String getIssuer() {
            return issuer;
        }

        public void setIssuer(String issuer) {
            this.issuer = issuer;
        }
    }

    public static class Admin {

        /** Логін адміністратора, якого створюється автоматично при першому запуску. */
        private String username = "admin";

        private String password = "admin123";

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }
    }

    public static class Storage {

        /** Каталог для зберігання скриншотів. */
        private String screenshotsPath = "./data/screenshots";

        /** Мінімальна кількість автоскриншотів на день. */
        private int minScreenshotsPerDay = 10;

        /** Максимальна кількість автоскриншотів на день. */
        private int maxScreenshotsPerDay = 35;

        public String getScreenshotsPath() {
            return screenshotsPath;
        }

        public void setScreenshotsPath(String screenshotsPath) {
            this.screenshotsPath = screenshotsPath;
        }

        public int getMinScreenshotsPerDay() {
            return minScreenshotsPerDay;
        }

        public void setMinScreenshotsPerDay(int minScreenshotsPerDay) {
            this.minScreenshotsPerDay = minScreenshotsPerDay;
        }

        public int getMaxScreenshotsPerDay() {
            return maxScreenshotsPerDay;
        }

        public void setMaxScreenshotsPerDay(int maxScreenshotsPerDay) {
            this.maxScreenshotsPerDay = maxScreenshotsPerDay;
        }
    }
}
