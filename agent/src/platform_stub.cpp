// Заглушка реалізації платформи для не-Windows систем.
// Дозволяє збирати й тестувати переносиму частину агента на Linux/macOS.

#include "mc/platform.hpp"

#include <cstdlib>
#include <fstream>
#include <iostream>
#include <sstream>
#include <unistd.h>

namespace mc {
namespace {

class StubPlatform final : public Platform {
public:
    std::string hostname() const override {
        char buffer[256]{};
        if (gethostname(buffer, sizeof(buffer) - 1) == 0) return buffer;
        return "unknown-host";
    }

    std::string macAddress() const override {
        std::ifstream file("/sys/class/net/lo/address");
        std::string mac;
        if (file) file >> mac;
        return mac;
    }

    std::string osName() const override { return "Linux"; }

    std::string osVersion() const override { return "unknown"; }

    std::string userName() const override {
        const char* user = std::getenv("USER");
        return user ? user : "";
    }

    std::string agentVersion() const override { return "0.1.0"; }

    bool foregroundWindow(WindowInfo* out) const override {
        (void)out;
        return false;
    }

    uint64_t idleSeconds() const override { return 0; }

    std::vector<ProcessInfo> runningProcesses() const override { return {}; }

    bool activeBrowser(BrowserInfo* out) const override {
        (void)out;
        return false;
    }

    bool lockWorkstation(const std::string&) override { return false; }

    bool captureScreen(std::vector<std::uint8_t>*) override { return false; }

    std::vector<std::uint8_t> protectSecret(const std::string& plain) const override {
        // Без криптографії: у реальній збірці це DPAPI (див. platform_win.cpp).
        return std::vector<std::uint8_t>(plain.begin(), plain.end());
    }

    bool unprotectSecret(const std::vector<std::uint8_t>& blob, std::string* out) const override {
        if (!out) return false;
        out->assign(blob.begin(), blob.end());
        return true;
    }

    bool installTamperProtection(const std::string&) override { return false; }

    void showMessage(const std::string& title, const std::string& text) override {
        std::cout << "[mc-agent] " << title << ": " << text << std::endl;
    }
};

}  // namespace

std::unique_ptr<Platform> createPlatform() {
    return std::make_unique<StubPlatform>();
}

}  // namespace mc
