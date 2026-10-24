// Мінімальний тестовий каркас (без зовнішніх залежностей).

#include "mc/activity.hpp"
#include "mc/config.hpp"
#include "mc/json.hpp"
#include "mc/platform.hpp"
#include "mc/screenshot.hpp"

#include <cmath>
#include <cstdio>
#include <iostream>
#include <string>
#include <unistd.h>
#include <vector>

namespace {

int g_failures = 0;
int g_checks = 0;

void check(bool condition, const std::string& what) {
    ++g_checks;
    if (!condition) {
        ++g_failures;
        std::cerr << "FAIL: " << what << "\n";
    }
}

void checkEqual(const std::string& actual, const std::string& expected, const std::string& what) {
    check(actual == expected, what + " (очікувалося '" + expected + "', отримано '" + actual + "')");
}

/// Тестова платформа: задає стан активного вікна та процесів.
class FakePlatform final : public mc::Platform {
public:
    mc::WindowInfo window;
    bool hasWindow = true;
    uint64_t idle = 0;
    std::vector<mc::ProcessInfo> processes;

    std::string hostname() const override { return "test-pc"; }
    std::string macAddress() const override { return "00:11:22:33:44:55"; }
    std::string osName() const override { return "Windows 11"; }
    std::string osVersion() const override { return "11.0"; }
    std::string userName() const override { return "manager"; }
    std::string agentVersion() const override { return "0.1.0"; }

    bool foregroundWindow(mc::WindowInfo* out) const override {
        if (!hasWindow) return false;
        *out = window;
        return true;
    }

    uint64_t idleSeconds() const override { return idle; }

    std::vector<mc::ProcessInfo> runningProcesses() const override { return processes; }

    bool activeBrowser(mc::BrowserInfo* out) const override { return false; }

    bool lockWorkstation(const std::string&) override { return true; }

    bool captureScreen(std::vector<std::uint8_t>*) override { return false; }

    std::vector<std::uint8_t> protectSecret(const std::string& plain) const override {
        return std::vector<std::uint8_t>(plain.begin(), plain.end());
    }

    bool unprotectSecret(const std::vector<std::uint8_t>& blob, std::string* out) const override {
        out->assign(blob.begin(), blob.end());
        return true;
    }

    bool installTamperProtection(const std::string&) override { return true; }

    void showMessage(const std::string&, const std::string&) override {}
};

void testJson() {
    mc::Json object = mc::Json::object();
    object.set("name", "chrome.exe");
    object.set("durationSeconds", 120);
    object.set("idle", false);
    object.set("tags", mc::Json(mc::JsonArray{"a", "b"}));

    checkEqual(object.dump(),
               R"({"durationSeconds":120,"idle":false,"name":"chrome.exe","tags":["a","b"]})",
               "JSON: серіалізація об'єкта");

    mc::Json parsed = mc::Json::parse(object.dump());
    check(parsed.isObject(), "JSON: розбір об'єкта");
    checkEqual(parsed.get("name").value_or(mc::Json()).asString(), "chrome.exe", "JSON: читання рядка");
    check(parsed.get("durationSeconds").value_or(mc::Json()).asInt() == 120, "JSON: читання числа");
    check(parsed.get("tags").value_or(mc::Json()).at(1).value_or(mc::Json()).asString() == "b",
          "JSON: читання масиву");

    std::string error;
    mc::Json broken = mc::Json::parse("{\"a\":", &error);
    check(broken.isNull(), "JSON: помилка розбору повертає null");
    check(!error.empty(), "JSON: текст помилки не порожній");

    mc::Json escaped = mc::Json::object();
    escaped.set("text", "line1\nline2 \"quoted\"");
    mc::Json reparsed = mc::Json::parse(escaped.dump());
    checkEqual(reparsed.get("text").value_or(mc::Json()).asString(), "line1\nline2 \"quoted\"",
               "JSON: екранування символів");
}

void testUrlExtraction() {
    std::string url, domain;
    // У заголовку Chrome домен не показується — витягнути URL звідти неможливо.
    check(mc::extractUrlFromTitle("Example Domain - Google Chrome", &url, &domain) == false,
          "URL: звичайний заголовок не вважається URL");

    check(mc::extractUrlFromTitle("https://docs.google.com/document/d/1 - Chrome", &url, &domain),
          "URL: домен не розпізнано з повного URL");
    checkEqual(domain, "docs.google.com", "URL: домен з повного URL");
    checkEqual(url, "https://docs.google.com/document/d/1", "URL: сам URL");

    check(mc::isKnownBrowser("chrome.exe", nullptr), "Браузер: chrome розпізнано");
    check(!mc::isKnownBrowser("excel.exe", nullptr), "Браузер: excel не є браузером");
}

void testScreenshotScheduler() {
    mc::ScreenshotScheduler scheduler(10, 35);
    const int64_t day = (1760000000000LL / 86400000LL) * 86400000LL;  // початок доби UTC
    check(scheduler.plannedToday() == 0, "Скриншоти: план ще не побудовано");
    check(scheduler.due(day) == false, "Скриншоти: до 08:00 скриншотів немає");

    int fired = 0;
    for (int64_t t = day; t < day + 24LL * 3600 * 1000; t += 60000) {
        if (scheduler.due(t)) ++fired;
    }
    check(fired >= 10 && fired <= 35, "Скриншоти: кількість за добу в межах 10..35");
    check(scheduler.capturedCount() == fired, "Скриншоти: лічильник збігається");
}

void testActivityTracker() {
    FakePlatform platform;
    platform.window.processName = "chrome.exe";
    platform.window.windowTitle = "Example";
    platform.processes = {mc::ProcessInfo{100, "chrome.exe", "C:\\chrome.exe"}};

    mc::ActivityTracker tracker(platform, 120);

    tracker.sample(1000);
    platform.window.processName = "excel.exe";
    tracker.sample(6000);

    auto intervals = tracker.takeUsageIntervals();
    check(intervals.size() == 1, "Активність: закрито один інтервал при зміні програми");
    if (!intervals.empty()) {
        checkEqual(intervals[0].processName, "chrome.exe", "Активність: назва програми");
        check(intervals[0].durationSeconds == 5, "Активність: тривалість інтервалу");
    }

    auto events = tracker.takeProcessEvents();
    check(events.size() == 1, "Активність: зафіксовано одну подію процесу");
    if (!events.empty()) {
        checkEqual(events[0].action, "STARTED", "Активність: тип події");
    }
}

void testConfig() {
    mc::Config config;
    mc::Config loaded;
    std::string error;

    check(mc::loadConfig("/tmp/mc-nonexistent.conf", loaded, &error) == false,
          "Конфіг: відсутній файл повертає помилку");

    mc::Config defaults;
    check(defaults.deviceId.empty(), "Конфіг: deviceId порожній за замовчуванням");

    const std::string id = mc::generateDeviceId();
    check(id.size() == 36, "Конфіг: довжина UUID");
    check(id[14] == '4', "Конфіг: версія UUID 4");
    check(mc::generateDeviceId() != id, "Конфіг: UUID унікальні");
}

}  // namespace

void testPlatformStubProcesses() {
#if !defined(MC_PLATFORM_WINDOWS)
    auto platform = mc::createPlatform();
    auto processes = platform->runningProcesses();

    check(!processes.empty(), "Платформа: список процесів не порожній на Linux");

    const uint64_t myPid = static_cast<uint64_t>(getpid());
    bool foundSelf = false;
    for (const auto& p : processes) {
        if (p.pid == myPid) {
            foundSelf = true;
            break;
        }
    }
    check(foundSelf, "Платформа: власний процес присутній у списку");
#endif
}

int main() {
    testJson();
    testUrlExtraction();
    testScreenshotScheduler();
    testActivityTracker();
    testConfig();
    testPlatformStubProcesses();

    std::cout << "Перевірок: " << g_checks << ", помилок: " << g_failures << "\n";
    return g_failures == 0 ? 0 : 1;
}

