#include "mc/config.hpp"

#include <algorithm>
#include <cctype>
#include <cstdlib>
#include <fstream>
#include <random>
#include <sstream>

namespace mc {
namespace {

std::string trim(const std::string& value) {
    size_t begin = value.find_first_not_of(" \t\r\n");
    if (begin == std::string::npos) return {};
    size_t end = value.find_last_not_of(" \t\r\n");
    return value.substr(begin, end - begin + 1);
}

std::string toLower(std::string value) {
    std::transform(value.begin(), value.end(), value.begin(),
                   [](unsigned char c) { return static_cast<char>(std::tolower(c)); });
    return value;
}

}  // namespace

std::string generateDeviceId() {
    std::random_device rd;
    std::mt19937 gen(rd());
    std::uniform_int_distribution<int> hex(0, 15);

    static const char* digits = "0123456789abcdef";
    std::string out;
    out.reserve(36);
    for (int i = 0; i < 36; ++i) {
        if (i == 8 || i == 13 || i == 18 || i == 23) {
            out.push_back('-');
        } else if (i == 14) {
            out.push_back('4');  // версія UUID 4
        } else if (i == 19) {
            out.push_back(digits[(hex(gen) & 0x3) | 0x8]);
        } else {
            out.push_back(digits[hex(gen)]);
        }
    }
    return out;
}

bool loadConfig(const std::string& path, Config& config, std::string* error) {
    std::ifstream file(path);
    if (!file) {
        if (error) *error = "не вдалося відкрити файл конфігурації: " + path;
        return false;
    }

    std::string line;
    int lineNumber = 0;
    while (std::getline(file, line)) {
        ++lineNumber;
        std::string trimmed = trim(line);
        if (trimmed.empty() || trimmed[0] == '#') continue;

        size_t eq = trimmed.find('=');
        if (eq == std::string::npos) {
            if (error) *error = "рядок " + std::to_string(lineNumber) + ": очікується key = value";
            return false;
        }
        std::string key = toLower(trim(trimmed.substr(0, eq)));
        std::string value = trim(trimmed.substr(eq + 1));

        if (key == "server_url") config.serverUrl = value;
        else if (key == "enrollment_code") config.enrollmentCode = value;
        else if (key == "verify_tls") config.verifyTls = toLower(value) != "false" && value != "0";
        else if (key == "config_path") config.configPath = value;
        else if (key == "state_path") config.statePath = value;
        else if (key == "sample_interval_seconds") config.sampleIntervalSeconds = std::atoi(value.c_str());
        else if (key == "idle_threshold_seconds") config.idleThresholdSeconds = std::atoi(value.c_str());
        else if (key == "heartbeat_seconds") config.heartbeatSeconds = std::atoi(value.c_str());
        else if (key == "upload_interval_seconds") config.uploadIntervalSeconds = std::atoi(value.c_str());
        else if (key == "location_interval_seconds") config.locationIntervalSeconds = std::atoi(value.c_str());
        else if (key == "min_screenshots_per_day") config.minScreenshotsPerDay = std::atoi(value.c_str());
        else if (key == "max_screenshots_per_day") config.maxScreenshotsPerDay = std::atoi(value.c_str());
        else if (key == "verbose") config.verbose = toLower(value) == "true" || value == "1";
        else if (key == "tamper_protection") config.tamperProtection = toLower(value) != "false" && value != "0";
        // невідомі ключі ігноруємо, щоб конфіг був сумісним між версіями
    }

    if (config.serverUrl.empty()) {
        if (error) *error = "server_url не може бути порожнім";
        return false;
    }
    if (error) error->clear();
    return true;
}

bool saveState(const std::string& path, const Config& config, std::string* error) {
    std::ofstream file(path, std::ios::trunc);
    if (!file) {
        if (error) *error = "не вдалося зберегти стан: " + path;
        return false;
    }
    file << "device_id = " << config.deviceId << "\n";
    file << "device_token = " << config.deviceToken << "\n";
    if (!file) {
        if (error) *error = "помилка запису файлу стану: " + path;
        return false;
    }
    if (error) error->clear();
    return true;
}

bool loadState(const std::string& path, Config& config, std::string* error) {
    std::ifstream file(path);
    if (!file) {
        if (error) *error = "файл стану не знайдено: " + path;
        return false;
    }

    std::string line;
    while (std::getline(file, line)) {
        std::string trimmed = trim(line);
        if (trimmed.empty() || trimmed[0] == '#') continue;
        size_t eq = trimmed.find('=');
        if (eq == std::string::npos) continue;
        std::string key = toLower(trim(trimmed.substr(0, eq)));
        std::string value = trim(trimmed.substr(eq + 1));
        if (key == "device_id") config.deviceId = value;
        else if (key == "device_token") config.deviceToken = value;
    }
    if (error) error->clear();
    return true;
}

}  // namespace mc
