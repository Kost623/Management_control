#include "mc/agent.hpp"
#include "mc/config.hpp"
#include "mc/platform.hpp"

#include <iostream>
#include <string>

namespace {

void printUsage() {
    std::cout << "Management Control Agent\n\n"
              << "Використання:\n"
              << "  mc-agent [--config <файл>] [--enroll <код>] [--server <url>] [--verbose]\n\n"
              << "Параметри:\n"
              << "  --config <файл>   файл конфігурації (за замовчуванням mc-agent.conf)\n"
              << "  --enroll <код>    код реєстрації пристрою (одноразовий)\n"
              << "  --server <url>    адреса сервера\n"
              << "  --state <файл>   файл зі збереженим токеном пристрою\n"
              << "  --verbose         детальний лог\n";
}

}  // namespace

int main(int argc, char** argv) {
    mc::Config config;

    for (int i = 1; i < argc; ++i) {
        std::string arg = argv[i];
        auto next = [&](const char* name) -> std::string {
            if (i + 1 >= argc) {
                std::cerr << "Параметр " << name << " потребує значення\n";
                std::exit(2);
            }
            return argv[++i];
        };

        if (arg == "--help" || arg == "-h") {
            printUsage();
            return 0;
        } else if (arg == "--config") {
            config.configPath = next("--config");
        } else if (arg == "--enroll") {
            config.enrollmentCode = next("--enroll");
        } else if (arg == "--server") {
            config.serverUrl = next("--server");
        } else if (arg == "--state") {
            config.statePath = next("--state");
        } else if (arg == "--verbose") {
            config.verbose = true;
        } else {
            std::cerr << "Невідомий параметр: " << arg << "\n\n";
            printUsage();
            return 2;
        }
    }

    std::string error;
    if (!mc::loadConfig(config.configPath, config, &error)) {
        // Відсутність конфігурації — не критично: працюємо з типовими значеннями.
        std::cerr << "[mc-agent] " << error << " (використовуються типові значення)\n";
    }
    // Параметри командного рядка мають пріоритет над файлом конфігурації.
    for (int i = 1; i < argc; ++i) {
        std::string arg = argv[i];
        if (arg == "--enroll" && i + 1 < argc) config.enrollmentCode = argv[i + 1];
        else if (arg == "--server" && i + 1 < argc) config.serverUrl = argv[i + 1];
        else if (arg == "--state" && i + 1 < argc) config.statePath = argv[i + 1];
    }

    auto platform = mc::createPlatform();
    mc::Agent agent(config, *platform);
    return agent.run();
}
