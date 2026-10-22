#pragma once

#include "mc/platform.hpp"

#include <cstdint>
#include <string>
#include <vector>

namespace mc {

/// Інтервал активного використання програми.
struct UsageInterval {
    std::string processName;
    std::string windowTitle;
    std::string exePath;
    int64_t startedAtMs = 0;
    int64_t endedAtMs = 0;
    int64_t durationSeconds = 0;
    bool idle = false;
    bool foreground = true;
};

/// Подія запуску або завершення процесу.
struct ProcessEvent {
    std::string processName;
    std::string exePath;
    std::string action;   // STARTED | EXITED
    int64_t occurredAtMs = 0;
};

/// Інтервал відвідування сайту.
struct BrowsingInterval {
    std::string url;
    std::string domain;
    std::string browserName;
    std::string tabTitle;
    int64_t startedAtMs = 0;
    int64_t endedAtMs = 0;
    int64_t durationSeconds = 0;
    bool idle = false;
};

/// Трекер активності: опитує ОС та накопичує інтервали використання.
class ActivityTracker {
public:
    ActivityTracker(Platform& platform, int idleThresholdSeconds);

    /// Опитування стану системи. Викликається раз на sampleIntervalSeconds.
    void sample(int64_t nowMs);

    /// Забирає накопичені закриті інтервали та очищає буфер.
    std::vector<UsageInterval> takeUsageIntervals();
    std::vector<BrowsingInterval> takeBrowsingIntervals();
    std::vector<ProcessEvent> takeProcessEvents();

    /// Поточна активна програма (для heartbeat).
    std::string currentProcessName() const { return currentProcess_; }

private:
    void closeCurrent(int64_t nowMs);
    void closeBrowsing(int64_t nowMs);
    void trackProcesses(int64_t nowMs);

    Platform& platform_;
    int idleThresholdSeconds_;

    bool hasCurrent_ = false;
    std::string currentProcess_;
    std::string currentWindowTitle_;
    std::string currentExePath_;
    bool currentForeground_ = false;
    int64_t currentStartedAtMs_ = 0;

    bool hasBrowsing_ = false;
    std::string currentUrl_;
    std::string currentDomain_;
    std::string currentBrowser_;
    std::string currentTabTitle_;
    int64_t browsingStartedAtMs_ = 0;

    std::vector<std::string> knownProcesses_;

    std::vector<UsageInterval> usageBuffer_;
    std::vector<BrowsingInterval> browsingBuffer_;
    std::vector<ProcessEvent> processBuffer_;
};

/// Перетворює мілісекунди epoch у формат ISO-8601 UTC (2026-10-22T09:15:00Z).
std::string formatTimestamp(int64_t epochMs);

}  // namespace mc
