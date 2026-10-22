#pragma once

// Абстракція над ОС: активне вікно, процеси, скриншоти, блокування, захист секретів.

#include <cstdint>
#include <memory>
#include <string>
#include <vector>

namespace mc {

struct WindowInfo {
    std::string processName;
    std::string windowTitle;
    std::string exePath;
    uint64_t pid = 0;
};

struct ProcessInfo {
    uint64_t pid = 0;
    std::string name;
    std::string exePath;
};

struct BrowserInfo {
    std::string browserName;
    std::string url;
    std::string title;
};

class Platform {
public:
    virtual ~Platform() = default;

    virtual std::string hostname() const = 0;
    virtual std::string macAddress() const = 0;
    virtual std::string osName() const = 0;
    virtual std::string osVersion() const = 0;
    virtual std::string userName() const = 0;
    virtual std::string agentVersion() const = 0;

    /// Активне (у фокусі) вікно.
    virtual bool foregroundWindow(WindowInfo* out) const = 0;

    /// Скільки секунд користувач нічого не робив.
    virtual uint64_t idleSeconds() const = 0;

    /// Список запущених процесів.
    virtual std::vector<ProcessInfo> runningProcesses() const = 0;

    /// Дані активного браузера (якщо вдалося визначити).
    virtual bool activeBrowser(BrowserInfo* out) const = 0;

    /// Заблокувати робочу станцію.
    virtual bool lockWorkstation(const std::string& reason) = 0;

    /// Зняти зображення екрана у форматі PNG.
    virtual bool captureScreen(std::vector<std::uint8_t>* png) = 0;

    /// Зашифрувати секрет так, щоб він читався лише цим користувачем.
    virtual std::vector<std::uint8_t> protectSecret(const std::string& plain) const = 0;

    /// Розшифрувати секрет.
    virtual bool unprotectSecret(const std::vector<std::uint8_t>& blob, std::string* out) const = 0;

    /// Встановити захист від видалення (watchdog + плановий службовий запис).
    virtual bool installTamperProtection(const std::string& executablePath) = 0;

    /// Вивести повідомлення користувачу.
    virtual void showMessage(const std::string& title, const std::string& text) = 0;
};

/// Створює реалізацію для поточної ОС.
std::unique_ptr<Platform> createPlatform();

/// Визначає домен з URL або заголовка вікна. Повертає порожній рядок, якщо домен не розпізнано.
std::string extractDomain(const std::string& text);

/// Визначає URL/домен у заголовку вікна браузера.
bool extractUrlFromTitle(const std::string& title, std::string* url, std::string* domain);

/// Перевіряє, чи процес належить браузеру, і повертає його назву.
bool isKnownBrowser(const std::string& processName, std::string* browserName);

}  // namespace mc
