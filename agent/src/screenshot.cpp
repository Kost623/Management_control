#include "mc/screenshot.hpp"

#include <algorithm>
#include <random>

namespace mc {
namespace {

constexpr int64_t kDayMs = 24LL * 60 * 60 * 1000;
/// Робочий день для розподілу скриншотів: 08:00 — 20:00.
constexpr int64_t kWorkdayStartHour = 8;
constexpr int64_t kWorkdayEndHour = 20;

int64_t dayStart(int64_t nowMs) {
    const int64_t day = nowMs / kDayMs;
    return day * kDayMs;
}

}  // namespace

ScreenshotScheduler::ScreenshotScheduler(int minPerDay, int maxPerDay)
    : minPerDay_(std::max(1, minPerDay)), maxPerDay_(std::max(minPerDay_, maxPerDay)) {}

void ScreenshotScheduler::planFor(int64_t nowMs) {
    planDayStartMs_ = dayStart(nowMs);
    moments_.clear();
    index_ = 0;

    std::random_device rd;
    std::mt19937 gen(rd());
    std::uniform_int_distribution<int> countDist(minPerDay_, maxPerDay_);
    std::uniform_int_distribution<int64_t> offsetDist(0, (kWorkdayEndHour - kWorkdayStartHour) * 3600 - 1);

    const int count = countDist(gen);
    for (int i = 0; i < count; ++i) {
        int64_t offsetSeconds = offsetDist(gen);
        moments_.push_back(planDayStartMs_ + kWorkdayStartHour * 3600 * 1000 + offsetSeconds * 1000);
    }
    std::sort(moments_.begin(), moments_.end());
}

bool ScreenshotScheduler::due(int64_t nowMs) {
    if (planDayStartMs_ != dayStart(nowMs)) {
        planFor(nowMs);
    }
    if (index_ >= moments_.size()) {
        return false;
    }
    if (nowMs >= moments_[index_]) {
        ++index_;
        ++captured_;
        return true;
    }
    return false;
}

}  // namespace mc
