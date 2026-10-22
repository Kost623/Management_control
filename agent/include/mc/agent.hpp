#pragma once

#include "mc/activity.hpp"
#include "mc/config.hpp"
#include "mc/http.hpp"
#include "mc/json.hpp"
#include "mc/platform.hpp"
#include "mc/screenshot.hpp"

#include <cstdint>
#include <memory>
#include <string>

namespace mc {

/// Основний цикл агента: реєстрація, збір даних, відправка на сервер, виконання команд.
class Agent {
public:
    Agent(Config config, Platform& platform);

    /// Запускає агент. Повертає код завершення.
    int run();

    /// Реєструє пристрій на сервері та зберігає токен. Публічно — для тестів.
    bool enroll();

private:
    void log(const std::string& message) const;
    int64_t nowMs() const;

    bool ensureRegistered();
    bool sendHeartbeat();
    bool flushUsage();
    bool flushBrowsing();
    bool flushProcesses();
    bool sendLocation();
    bool uploadScreenshot(const std::vector<std::uint8_t>& png, const std::string& trigger);
    void pollCommands();
    void handleCommand(const Json& command);

    Config config_;
    Platform& platform_;
    HttpClient http_;
    std::unique_ptr<ActivityTracker> tracker_;
    std::unique_ptr<ScreenshotScheduler> scheduler_;

    int64_t lastHeartbeatMs_ = 0;
    int64_t lastUploadMs_ = 0;
    int64_t lastLocationMs_ = 0;
    int64_t lastSampleMs_ = 0;
    bool registered_ = false;
};

}  // namespace mc
