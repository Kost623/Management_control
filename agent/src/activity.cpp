#include "mc/activity.hpp"

#include <algorithm>
#include <cstdio>
#include <ctime>
#include <set>

namespace mc {

std::string formatTimestamp(int64_t epochMs) {
    std::time_t seconds = static_cast<std::time_t>(epochMs / 1000);
    std::tm tmValue{};
#if defined(_WIN32)
    gmtime_s(&tmValue, &seconds);
#else
    gmtime_r(&seconds, &tmValue);
#endif
    char buffer[32];
    std::strftime(buffer, sizeof(buffer), "%Y-%m-%dT%H:%M:%SZ", &tmValue);
    return buffer;
}

ActivityTracker::ActivityTracker(Platform& platform, int idleThresholdSeconds)
    : platform_(platform), idleThresholdSeconds_(idleThresholdSeconds > 0 ? idleThresholdSeconds : 120) {}

void ActivityTracker::closeCurrent(int64_t nowMs) {
    if (!hasCurrent_) return;

    UsageInterval interval;
    interval.processName = currentProcess_;
    interval.windowTitle = currentWindowTitle_;
    interval.exePath = currentExePath_;
    interval.startedAtMs = currentStartedAtMs_;
    interval.endedAtMs = nowMs;
    interval.durationSeconds = std::max<int64_t>(0, (nowMs - currentStartedAtMs_) / 1000);
    interval.idle = false;
    interval.foreground = currentForeground_;
    usageBuffer_.push_back(std::move(interval));

    hasCurrent_ = false;
}

void ActivityTracker::closeBrowsing(int64_t nowMs) {
    if (!hasBrowsing_) return;

    BrowsingInterval interval;
    interval.url = currentUrl_;
    interval.domain = currentDomain_;
    interval.browserName = currentBrowser_;
    interval.tabTitle = currentTabTitle_;
    interval.startedAtMs = browsingStartedAtMs_;
    interval.endedAtMs = nowMs;
    interval.durationSeconds = std::max<int64_t>(0, (nowMs - browsingStartedAtMs_) / 1000);
    interval.idle = false;
    browsingBuffer_.push_back(std::move(interval));

    hasBrowsing_ = false;
}

void ActivityTracker::trackProcesses(int64_t nowMs) {
    std::vector<ProcessInfo> processes = platform_.runningProcesses();
    std::set<std::string> current;
    for (const auto& process : processes) {
        if (process.name.empty()) continue;
        current.insert(process.name);
    }

    for (const auto& name : current) {
        if (std::find(knownProcesses_.begin(), knownProcesses_.end(), name) == knownProcesses_.end()) {
            ProcessEvent event;
            event.processName = name;
            event.action = "STARTED";
            event.occurredAtMs = nowMs;
            processBuffer_.push_back(std::move(event));
        }
    }
    for (const auto& name : knownProcesses_) {
        if (current.find(name) == current.end()) {
            ProcessEvent event;
            event.processName = name;
            event.action = "EXITED";
            event.occurredAtMs = nowMs;
            processBuffer_.push_back(std::move(event));
        }
    }
    knownProcesses_ = std::vector<std::string>(current.begin(), current.end());
}

void ActivityTracker::sample(int64_t nowMs) {
    trackProcesses(nowMs);

    WindowInfo window;
    bool haveWindow = platform_.foregroundWindow(&window);
    uint64_t idle = platform_.idleSeconds();
    bool isIdle = idle >= static_cast<uint64_t>(idleThresholdSeconds_);

    // --- програма ---
    std::string processName = haveWindow ? window.processName : std::string();
    std::string windowTitle = haveWindow ? window.windowTitle : std::string();
    std::string exePath = haveWindow ? window.exePath : std::string();

    bool changed = !hasCurrent_ || processName != currentProcess_ || windowTitle != currentWindowTitle_;
    if (changed) {
        closeCurrent(nowMs);
        hasCurrent_ = true;
        currentProcess_ = processName.empty() ? "(none)" : processName;
        currentWindowTitle_ = windowTitle;
        currentExePath_ = exePath;
        currentForeground_ = haveWindow;
        currentStartedAtMs_ = nowMs;
    }

    // --- браузер ---
    BrowserInfo browser;
    std::string browserName;
    bool browserActive = false;
    if (haveWindow && isKnownBrowser(processName, &browserName)) {
        browser.browserName = browserName;
        browser.title = windowTitle;
        // Повний URL доступний лише через UI Automation; поки що беремо його з заголовка вікна.
        std::string url, domain;
        if (extractUrlFromTitle(windowTitle, &url, &domain)) {
            browser.url = url;
        }
        browserActive = true;
    }

    if (browserActive) {
        std::string domain;
        if (browser.url.empty()) {
            domain = extractDomain(browser.title);
        } else {
            extractUrlFromTitle(browser.url, nullptr, &domain);
        }
        bool browsingChanged = !hasBrowsing_ || browser.url != currentUrl_ || domain != currentDomain_;
        if (browsingChanged) {
            closeBrowsing(nowMs);
            hasBrowsing_ = true;
            currentUrl_ = browser.url;
            currentDomain_ = domain;
            currentBrowser_ = browser.browserName;
            currentTabTitle_ = browser.title;
            browsingStartedAtMs_ = nowMs;
        }
    } else if (hasBrowsing_) {
        closeBrowsing(nowMs);
    }

    // Час idle не зараховується як активна робота: позначаємо поточний інтервал.
    if (isIdle && hasCurrent_) {
        currentForeground_ = false;
    }
}

std::vector<UsageInterval> ActivityTracker::takeUsageIntervals() {
    std::vector<UsageInterval> result;
    result.swap(usageBuffer_);
    return result;
}

std::vector<BrowsingInterval> ActivityTracker::takeBrowsingIntervals() {
    std::vector<BrowsingInterval> result;
    result.swap(browsingBuffer_);
    return result;
}

std::vector<ProcessEvent> ActivityTracker::takeProcessEvents() {
    std::vector<ProcessEvent> result;
    result.swap(processBuffer_);
    return result;
}

}  // namespace mc
