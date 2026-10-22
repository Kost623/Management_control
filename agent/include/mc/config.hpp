#pragma once

#include <string>

namespace mc {

/// Конфігурація агента та збережена реєстрація пристрою.
struct Config {
    // --- server ---
    std::string serverUrl = "http://127.0.0.1:8080";
    std::string enrollmentCode;   // одноразовий код для першої реєстрації
    bool verifyTls = true;

    // --- device ---
    std::string deviceId;         // генерується при першому запуску
    std::string deviceToken;      // отриманий від сервера токен

    // --- paths ---
    std::string configPath = "mc-agent.conf";
    std::string statePath = "mc-agent.state";

    // --- collection ---
    int sampleIntervalSeconds = 5;
    int idleThresholdSeconds = 120;
    int heartbeatSeconds = 60;
    int uploadIntervalSeconds = 30;
    int locationIntervalSeconds = 300;
    int minScreenshotsPerDay = 10;
    int maxScreenshotsPerDay = 35;

    // --- misc ---
    bool verbose = false;
    bool tamperProtection = true;
};

/// Читає конфігурацію з файлу (формат key = value, # — коментар).
/// Відсутні ключі залишаються зі значеннями за замовчуванням.
bool loadConfig(const std::string& path, Config& config, std::string* error);

/// Зберігає реєстрацію пристрою (deviceId + зашифрований токен).
bool saveState(const std::string& path, const Config& config, std::string* error);

/// Читає збережену реєстрацію. deviceId генерується, якщо його ще немає.
bool loadState(const std::string& path, Config& config, std::string* error);

/// Створює випадковий ідентифікатор пристрою у форматі UUID v4.
std::string generateDeviceId();

}  // namespace mc
