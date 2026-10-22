#pragma once

#include <cstdint>
#include <vector>

namespace mc {

/// Планувальник автоматичних скриншотів.
/// Випадковий розподіл 10–35 скриншотів на добу в робочому часі.
class ScreenshotScheduler {
public:
    ScreenshotScheduler(int minPerDay, int maxPerDay);

    /// Чи настав час зробити наступний скриншот. Якщо так — повертає true
    /// і просуває план. Викликається раз на цикл опитування.
    bool due(int64_t nowMs);

    /// Кількість скриншотів, запланованих на поточну добу.
    int plannedToday() const { return static_cast<int>(moments_.size()); }

    /// Кількість зроблених скриншотів (від моменту запуску планувальника).
    int capturedCount() const { return captured_; }

private:
    void planFor(int64_t nowMs);

    int minPerDay_;
    int maxPerDay_;
    std::vector<int64_t> moments_;
    size_t index_ = 0;
    int captured_ = 0;
    int64_t planDayStartMs_ = -1;
};

}  // namespace mc
