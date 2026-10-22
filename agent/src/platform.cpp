#include "mc/platform.hpp"

#include <algorithm>
#include <cctype>
#include <cstdlib>

namespace mc {
namespace {

std::string toLower(std::string value) {
    std::transform(value.begin(), value.end(), value.begin(),
                   [](unsigned char c) { return static_cast<char>(std::tolower(c)); });
    return value;
}

bool isBrowserProcess(const std::string& processName, std::string* browserName) {
    static const struct {
        const char* exe;
        const char* name;
    } kBrowsers[] = {
        {"chrome.exe", "Google Chrome"},
        {"msedge.exe", "Microsoft Edge"},
        {"brave.exe", "Brave"},
        {"opera.exe", "Opera"},
        {"vivaldi.exe", "Vivaldi"},
        {"firefox.exe", "Firefox"},
        {"chromium.exe", "Chromium"},
        {"iexplore.exe", "Internet Explorer"},
    };

    const std::string lower = toLower(processName);
    for (const auto& browser : kBrowsers) {
        if (lower == browser.exe) {
            if (browserName) *browserName = browser.name;
            return true;
        }
    }
    return false;
}

}  // namespace

bool extractUrlFromTitle(const std::string& title, std::string* url, std::string* domain) {
    // Шукаємо схему http(s)://
    size_t start = title.find("://");
    if (start == std::string::npos) return false;
    size_t schemeStart = title.rfind(" ", start);
    schemeStart = (schemeStart == std::string::npos) ? 0 : schemeStart + 1;

    size_t end = start + 3;
    while (end < title.size() && !std::isspace(static_cast<unsigned char>(title[end])) &&
           title[end] != '"' && title[end] != '\'') {
        ++end;
    }
    if (end <= start + 3) return false;

    std::string candidate = title.substr(schemeStart, end - schemeStart);
    // відкидаємо хвост після пробілу в URL
    while (!candidate.empty() && (candidate.back() == ')' || candidate.back() == ']')) {
        candidate.pop_back();
    }

    std::string host;
    size_t hostStart = candidate.find("://");
    hostStart = (hostStart == std::string::npos) ? 0 : hostStart + 3;
    size_t hostEnd = candidate.find_first_of("/?#", hostStart);
    host = (hostEnd == std::string::npos) ? candidate.substr(hostStart) : candidate.substr(hostStart, hostEnd - hostStart);
    size_t at = host.find('@');
    if (at != std::string::npos) host = host.substr(at + 1);
    size_t colon = host.find(':');
    if (colon != std::string::npos) host = host.substr(0, colon);
    host = toLower(host);
    if (host.empty() || host.find('.') == std::string::npos) return false;

    if (url) *url = candidate;
    if (domain) *domain = host;
    return true;
}

std::string extractDomain(const std::string& text) {
    std::string url, domain;
    if (extractUrlFromTitle(text, &url, &domain)) {
        return domain;
    }
    return {};
}

bool isKnownBrowser(const std::string& processName, std::string* browserName) {
    return isBrowserProcess(processName, browserName);
}

}  // namespace mc
