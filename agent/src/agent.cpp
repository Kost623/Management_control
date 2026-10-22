#include "mc/agent.hpp"

#include <chrono>
#include <cstdio>
#include <iostream>
#include <thread>

namespace mc {
namespace {

constexpr const char* kApiBase = "/api/v1";

}  // namespace

Agent::Agent(Config config, Platform& platform)
    : config_(std::move(config)), platform_(platform) {
    http_.setBaseUrl(config_.serverUrl);
    http_.setBearerToken(config_.deviceToken);
    http_.setVerifyPeer(config_.verifyTls);
    tracker_ = std::make_unique<ActivityTracker>(platform_, config_.idleThresholdSeconds);
    scheduler_ = std::make_unique<ScreenshotScheduler>(config_.minScreenshotsPerDay,
                                                      config_.maxScreenshotsPerDay);
}

int64_t Agent::nowMs() const {
    using namespace std::chrono;
    return duration_cast<milliseconds>(system_clock::now().time_since_epoch()).count();
}

void Agent::log(const std::string& message) const {
    if (!config_.verbose) return;
    std::cerr << "[mc-agent] " << message << std::endl;
}

bool Agent::enroll() {
    if (config_.enrollmentCode.empty()) {
        std::cerr << "[mc-agent] немає коду реєстрації (enrollment_code у конфігурації)\n";
        return false;
    }

    Json request = Json::object();
    request.set("code", config_.enrollmentCode);
    request.set("deviceId", config_.deviceId);
    request.set("hostname", platform_.hostname());
    request.set("osName", platform_.osName());
    request.set("osVersion", platform_.osVersion());
    request.set("agentVersion", platform_.agentVersion());
    request.set("macAddress", platform_.macAddress());
    request.set("ipAddress", std::string());
    request.set("osUser", platform_.userName());

    HttpResponse response = http_.postJson(std::string(kApiBase) + "/agent/enroll", request.dump());
    if (!response.ok()) {
        std::cerr << "[mc-agent] реєстрація не вдалася (HTTP " << response.status << "): "
                  << response.body << "\n";
        return false;
    }

    Json body = Json::parse(response.body);
    auto token = body.get("token");
    if (!token || !token->isString() || token->asString().empty()) {
        std::cerr << "[mc-agent] сервер не повернув токен пристрою\n";
        return false;
    }

    config_.deviceToken = token->asString();
    http_.setBearerToken(config_.deviceToken);
    registered_ = true;

    std::string error;
    if (!saveState(config_.statePath, config_, &error)) {
        std::cerr << "[mc-agent] " << error << "\n";
    }
    std::cout << "[mc-agent] пристрій " << config_.deviceId << " зареєстровано" << std::endl;
    return true;
}

bool Agent::ensureRegistered() {
    if (registered_ && !config_.deviceToken.empty()) {
        return true;
    }
    std::string error;
    if (loadState(config_.statePath, config_, &error) && !config_.deviceToken.empty()) {
        http_.setBearerToken(config_.deviceToken);
        registered_ = true;
        log("використано збережену реєстрацію");
        return true;
    }
    return enroll();
}

bool Agent::sendHeartbeat() {
    Json request = Json::object();
    request.set("agentVersion", platform_.agentVersion());
    request.set("loggedInUser", platform_.userName());
    request.set("tamperStatus", config_.tamperProtection ? "enabled" : "disabled");

    HttpResponse response = http_.postJson(std::string(kApiBase) + "/agent/heartbeat", request.dump());
    if (!response.ok()) {
        log("heartbeat не вдався: HTTP " + std::to_string(response.status));
        return false;
    }
    lastHeartbeatMs_ = nowMs();
    return true;
}

bool Agent::flushUsage() {
    auto intervals = tracker_->takeUsageIntervals();
    if (intervals.empty()) return true;

    Json sessions = Json::array();
    for (const auto& interval : intervals) {
        Json item = Json::object();
        item.set("processName", interval.processName);
        item.set("windowTitle", interval.windowTitle);
        item.set("exePath", interval.exePath);
        item.set("startedAt", formatTimestamp(interval.startedAtMs));
        item.set("endedAt", formatTimestamp(interval.endedAtMs));
        item.set("durationSeconds", static_cast<long long>(interval.durationSeconds));
        item.set("idle", interval.idle);
        item.set("foreground", interval.foreground);
        sessions.push(item);
    }

    Json body = Json::object();
    body.set("sessions", sessions);
    HttpResponse response = http_.postJson(std::string(kApiBase) + "/agent/usage-sessions", body.dump());
    if (!response.ok()) {
        log("сесії не відправлено: HTTP " + std::to_string(response.status));
        return false;
    }
    return true;
}

bool Agent::flushBrowsing() {
    auto intervals = tracker_->takeBrowsingIntervals();
    if (intervals.empty()) return true;

    Json activities = Json::array();
    for (const auto& interval : intervals) {
        Json item = Json::object();
        item.set("url", interval.url);
        item.set("domain", interval.domain);
        item.set("browserName", interval.browserName);
        item.set("tabTitle", interval.tabTitle);
        item.set("startedAt", formatTimestamp(interval.startedAtMs));
        item.set("endedAt", formatTimestamp(interval.endedAtMs));
        item.set("durationSeconds", static_cast<long long>(interval.durationSeconds));
        item.set("idle", interval.idle);
        activities.push(item);
    }
    if (activities.items().empty()) return true;

    Json body = Json::object();
    body.set("activities", activities);
    HttpResponse response = http_.postJson(std::string(kApiBase) + "/agent/browsing", body.dump());
    if (!response.ok()) {
        log("відвідування не відправлено: HTTP " + std::to_string(response.status));
        return false;
    }
    return true;
}

bool Agent::flushProcesses() {
    auto events = tracker_->takeProcessEvents();
    if (events.empty()) return true;

    Json list = Json::array();
    for (const auto& event : events) {
        Json item = Json::object();
        item.set("processName", event.processName);
        item.set("exePath", event.exePath);
        item.set("action", event.action);
        item.set("occurredAt", formatTimestamp(event.occurredAtMs));
        list.push(item);
    }

    Json body = Json::object();
    body.set("events", list);
    HttpResponse response = http_.postJson(std::string(kApiBase) + "/agent/processes", body.dump());
    if (!response.ok()) {
        log("події процесів не відправлено: HTTP " + std::to_string(response.status));
        return false;
    }
    return true;
}

bool Agent::sendLocation() {
    // Геолокація за IP — точність до кількох кілометрів.
    const std::string url = "http://ip-api.com/json/?fields=status,message,lat,lon";
    http_.setBaseUrl("");
    HttpResponse response = http_.get(url);
    http_.setBaseUrl(config_.serverUrl);
    if (!response.ok()) {
        log("локація недоступна: HTTP " + std::to_string(response.status));
        return false;
    }

    Json body = Json::parse(response.body);
    auto status = body.get("status");
    if (!status || status->asString() != "success") {
        log("локація недоступна: " + body.dump());
        return false;
    }
    auto lat = body.get("lat");
    auto lon = body.get("lon");
    if (!lat || !lon) return false;

    Json request = Json::object();
    request.set("latitude", lat->asNumber());
    request.set("longitude", lon->asNumber());
    request.set("capturedAt", formatTimestamp(nowMs()));
    request.set("source", std::string("WIFI"));

    HttpResponse ack = http_.postJson(std::string(kApiBase) + "/agent/location", request.dump());
    lastLocationMs_ = nowMs();
    if (!ack.ok()) {
        log("локація не відправлена: HTTP " + std::to_string(ack.status));
        return false;
    }
    return true;
}

bool Agent::uploadScreenshot(const std::vector<std::uint8_t>& png, const std::string& trigger) {
    if (png.empty()) return false;

    QueryParams fields;
    fields.emplace_back("takenAt", formatTimestamp(nowMs()));
    fields.emplace_back("trigger", trigger);

    HttpResponse response = http_.postMultipart(std::string(kApiBase) + "/agent/screenshots",
                                                "file", "screen.png", png, fields);
    if (!response.ok()) {
        log("скриншот не відправлено: HTTP " + std::to_string(response.status));
        return false;
    }
    return true;
}

void Agent::handleCommand(const Json& command) {
    auto id = command.get("id");
    auto type = command.get("type");
    if (!id || !type) return;

    const std::string commandType = type->asString();
    const long long commandId = id->asInt();
    std::string result;
    std::string status = "EXECUTED";

    log("команда " + commandType + " #" + std::to_string(commandId));

    if (commandType == "LOCK") {
        auto payload = command.get("payload");
        platform_.lockWorkstation(payload ? payload->asString() : "");
        result = "lock requested";
    } else if (commandType == "UNLOCK") {
        result = "unlock requires local credentials";
    } else if (commandType == "TAKE_SCREENSHOT") {
        std::vector<std::uint8_t> png;
        if (platform_.captureScreen(&png)) {
            uploadScreenshot(png, "COMMAND");
            result = "screenshot uploaded";
        } else {
            status = "FAILED";
            result = "screenshot failed";
        }
    } else if (commandType == "SHOW_MESSAGE") {
        auto payload = command.get("payload");
        platform_.showMessage("Management Control", payload ? payload->asString() : "");
        result = "message shown";
    } else if (commandType == "REBOOT") {
        status = "FAILED";
        result = "reboot is not implemented";
    } else {
        status = "FAILED";
        result = "unsupported command";
    }

    Json ack = Json::object();
    ack.set("commandId", commandId);
    ack.set("status", status);
    ack.set("result", result);
    http_.postJson(std::string(kApiBase) + "/agent/commands/" + std::to_string(commandId) + "/ack",
                   ack.dump());
}

void Agent::pollCommands() {
    HttpResponse response = http_.get(std::string(kApiBase) + "/agent/commands",
                                      {{"timeoutSeconds", std::to_string(config_.sampleIntervalSeconds)}});
    if (!response.ok()) {
        log("команди не отримано: HTTP " + std::to_string(response.status));
        return;
    }
    Json body = Json::parse(response.body);
    if (!body.isArray()) return;
    for (const auto& command : body.items()) {
        handleCommand(command);
    }
}

int Agent::run() {
    if (config_.deviceId.empty()) {
        config_.deviceId = generateDeviceId();
    }

    if (!ensureRegistered()) {
        std::cerr << "[mc-agent] не вдалося зареєструвати пристрій\n";
        return 1;
    }

    if (config_.tamperProtection) {
        platform_.installTamperProtection(config_.statePath);
    }

    const int64_t sampleIntervalMs = static_cast<int64_t>(std::max(1, config_.sampleIntervalSeconds)) * 1000;
    const int64_t heartbeatIntervalMs = static_cast<int64_t>(std::max(5, config_.heartbeatSeconds)) * 1000;
    const int64_t uploadIntervalMs = static_cast<int64_t>(std::max(5, config_.uploadIntervalSeconds)) * 1000;
    const int64_t locationIntervalMs = static_cast<int64_t>(std::max(30, config_.locationIntervalSeconds)) * 1000;

    int64_t last = nowMs();
    while (true) {
        const int64_t current = nowMs();
        tracker_->sample(current);

        if (current - lastHeartbeatMs_ >= heartbeatIntervalMs) {
            if (sendHeartbeat()) {
                lastHeartbeatMs_ = current;
            }
        }
        if (current - lastUploadMs_ >= uploadIntervalMs) {
            flushUsage();
            flushBrowsing();
            flushProcesses();
            lastUploadMs_ = current;
        }
        if (current - lastLocationMs_ >= locationIntervalMs) {
            sendLocation();
            lastLocationMs_ = current;
        }
        if (scheduler_->due(current)) {
            std::vector<std::uint8_t> png;
            if (platform_.captureScreen(&png)) {
                uploadScreenshot(png, "AUTO_RANDOM");
            }
        }

        pollCommands();

        last = nowMs();
        int64_t sleepMs = sampleIntervalMs - (last - current);
        if (sleepMs < 0) sleepMs = 0;
        std::this_thread::sleep_for(std::chrono::milliseconds(sleepMs));
    }
}

}  // namespace mc
