// Реалізація платформи для Windows (Win32 + GDI+).
//
// Збірка: MSVC (CL), CMake, Visual Studio 2022.
// Лінкування: gdiplus, ntdll, ole32, gdi32, user32, advapi32, psapi, shlwapi, shell32.

#include "mc/platform.hpp"

#ifdef _WIN32

#include <windows.h>
#include <gdiplus.h>
#include <iphlpapi.h>
#include <objidl.h>
#include <psapi.h>
#include <tlhelp32.h>
#include <wincrypt.h>

#include <cstring>
#include <string>
#include <vector>

#pragma comment(lib, "gdiplus.lib")
#pragma comment(lib, "iphlpapi.lib")
#pragma comment(lib, "advapi32.lib")
#pragma comment(lib, "psapi.lib")
#pragma comment(lib, "shell32.lib")

namespace mc {
namespace {

std::string narrow(const wchar_t* text) {
    if (!text) return {};
    int size = WideCharToMultiByte(CP_UTF8, 0, text, -1, nullptr, 0, nullptr, nullptr);
    if (size <= 1) return {};
    std::string out(static_cast<size_t>(size - 1), '\0');
    WideCharToMultiByte(CP_UTF8, 0, text, -1, out.data(), size, nullptr, nullptr);
    return out;
}

std::wstring widen(const std::string& text) {
    if (text.empty()) return {};
    int size = MultiByteToWideChar(CP_UTF8, 0, text.c_str(), -1, nullptr, 0);
    if (size <= 1) return {};
    std::wstring out(static_cast<size_t>(size - 1), L'\0');
    MultiByteToWideChar(CP_UTF8, 0, text.c_str(), -1, out.data(), size);
    return out;
}

std::string processImagePath(HANDLE process) {
    if (!process) return {};
    wchar_t buffer[MAX_PATH]{};
    DWORD size = MAX_PATH;
    if (QueryFullProcessImageNameW(process, 0, buffer, &size)) {
        return narrow(buffer);
    }
    return {};
}

class WindowsPlatform final : public Platform {
public:
    std::string hostname() const override {
        wchar_t buffer[MAX_COMPUTERNAME_LENGTH + 1]{};
        DWORD size = MAX_COMPUTERNAME_LENGTH + 1;
        if (GetComputerNameW(buffer, &size)) {
            return narrow(buffer);
        }
        return {};
    }

    std::string macAddress() const override {
        ULONG size = 15 * 1024;
        std::vector<uint8_t> buffer(size);
        ULONG flags = GAA_FLAG_SKIP_ANYCAST | GAA_FLAG_SKIP_MULTICAST | GAA_FLAG_SKIP_DNS_SERVER;
        if (GetAdaptersAddresses(AF_INET, flags, nullptr,
                                 reinterpret_cast<IP_ADAPTER_ADDRESSES*>(buffer.data()),
                                 &size) == ERROR_BUFFER_OVERFLOW) {
            buffer.resize(size);
            if (GetAdaptersAddresses(AF_INET, flags, nullptr,
                                     reinterpret_cast<IP_ADAPTER_ADDRESSES*>(buffer.data()),
                                     &size) != NO_ERROR) {
                return {};
            }
        }
        auto* adapters = reinterpret_cast<IP_ADAPTER_ADDRESSES*>(buffer.data());
        for (auto* adapter = adapters; adapter; adapter = adapter->Next) {
            if (adapter->PhysicalAddressLength == 0) continue;
            char text[32]{};
            std::snprintf(text, sizeof(text), "%02X:%02X:%02X:%02X:%02X:%02X",
                          adapter->PhysicalAddress[0], adapter->PhysicalAddress[1],
                          adapter->PhysicalAddress[2], adapter->PhysicalAddress[3],
                          adapter->PhysicalAddress[4], adapter->PhysicalAddress[5]);
            return text;
        }
        return {};
    }

    std::string osName() const override { return "Windows"; }

    std::string osVersion() const override {
        using RtlGetVersionFn = LONG(WINAPI*)(PRTL_OSVERSIONINFOW);
        static const auto rtlGetVersion = reinterpret_cast<RtlGetVersionFn>(
            GetProcAddress(GetModuleHandleW(L"ntdll.dll"), "RtlGetVersion"));
        if (!rtlGetVersion) return {};

        RTL_OSVERSIONINFOW info{};
        info.dwOSVersionInfoSize = sizeof(info);
        if (rtlGetVersion(&info) != 0) return {};

        char text[64]{};
        std::snprintf(text, sizeof(text), "%lu.%lu.%lu", info.dwMajorVersion,
                      info.dwMinorVersion, info.dwBuildNumber);
        return text;
    }

    std::string userName() const override {
        wchar_t buffer[UNLEN + 1]{};
        DWORD size = sizeof(buffer) / sizeof(wchar_t);
        if (GetUserNameW(buffer, &size)) {
            return narrow(buffer);
        }
        return {};
    }

    std::string agentVersion() const override { return "0.1.0"; }

    bool foregroundWindow(WindowInfo* out) const override {
        if (!out) return false;
        HWND window = GetForegroundWindow();
        if (!window) return false;

        DWORD pid = 0;
        GetWindowThreadProcessId(window, &pid);

        wchar_t title[1024]{};
        GetWindowTextW(window, title, static_cast<int>(std::size(title)));

        out->pid = pid;
        out->windowTitle = narrow(title);

        HANDLE process = OpenProcess(PROCESS_QUERY_LIMITED_INFORMATION, FALSE, pid);
        if (process) {
            out->exePath = processImagePath(process);
            CloseHandle(process);
        }

        const std::string path = out->exePath;
        size_t slash = path.find_last_of("\\/");
        out->processName = (slash == std::string::npos) ? path : path.substr(slash + 1);
        return !out->processName.empty();
    }

    uint64_t idleSeconds() const override {
        LASTINPUTINFO info{};
        info.cbSize = sizeof(info);
        if (!GetLastInputInfo(&info)) return 0;
        DWORD tick = GetTickCount();
        return (tick >= info.dwTime) ? (tick - info.dwTime) / 1000ULL : 0ULL;
    }

    std::vector<ProcessInfo> runningProcesses() const override {
        std::vector<ProcessInfo> result;
        HANDLE snapshot = CreateToolhelp32Snapshot(TH32CS_SNAPPROCESS, 0);
        if (snapshot == INVALID_HANDLE_VALUE) return result;

        PROCESSENTRY32W entry{};
        entry.dwSize = sizeof(entry);
        if (Process32FirstW(snapshot, &entry)) {
            do {
                ProcessInfo info;
                info.pid = entry.th32ProcessID;
                info.name = narrow(entry.szExeFile);
                HANDLE process = OpenProcess(PROCESS_QUERY_LIMITED_INFORMATION, FALSE, entry.th32ProcessID);
                if (process) {
                    info.exePath = processImagePath(process);
                    CloseHandle(process);
                }
                result.push_back(std::move(info));
            } while (Process32NextW(snapshot, &entry));
        }
        CloseHandle(snapshot);
        return result;
    }

    bool activeBrowser(BrowserInfo* out) const override {
        if (!out) return false;
        WindowInfo window;
        if (!foregroundWindow(&window)) return false;

        std::string browserName;
        if (!isKnownBrowser(window.processName, &browserName)) return false;

        out->browserName = browserName;
        out->title = window.windowTitle;
        std::string url, domain;
        if (extractUrlFromTitle(window.windowTitle, &url, &domain)) {
            out->url = url;
        }
        return true;
    }

    bool lockWorkstation(const std::string& reason) override {
        (void)reason;
        return LockWorkStation() != FALSE;
    }

    bool captureScreen(std::vector<std::uint8_t>* png) override {
        if (!png) return false;

        const int width = GetSystemMetrics(SM_CXVIRTUALSCREEN);
        const int height = GetSystemMetrics(SM_CYVIRTUALSCREEN);
        if (width <= 0 || height <= 0) return false;
        const int left = GetSystemMetrics(SM_XVIRTUALSCREEN);
        const int top = GetSystemMetrics(SM_YVIRTUALSCREEN);

        GdiplusStartupInput startupInput;
        ULONG_PTR token = 0;
        if (GdiplusStartup(&token, &startupInput, nullptr) != Ok) {
            return false;
        }

        bool saved = false;
        {
            Gdiplus::Bitmap bitmap(width, height, PixelFormat32bppARGB);
            Gdiplus::Graphics graphics(&bitmap);
            graphics.CopyFromScreen(left, top, 0, 0, Gdiplus::Size(width, height));

            CLSID pngClsid;
            if (GetImageEncoderInfo(PixelFormat32bppARGB, &pngClsid) == Ok) {
                IStream* stream = nullptr;
                if (CreateStreamOnHGlobal(nullptr, TRUE, &stream) == S_OK) {
                    if (bitmap.Save(stream, &pngClsid, nullptr) == Ok) {
                        HGLOBAL global = nullptr;
                        if (GetHGlobalFromStream(stream, &global) == S_OK) {
                            const SIZE_T size = GlobalSize(global);
                            const auto* data = static_cast<const uint8_t*>(GlobalLock(global));
                            if (data && size > 0) {
                                png->assign(data, data + size);
                                saved = true;
                            }
                            GlobalUnlock(global);
                        }
                    }
                    stream->Release();
                }
            }
        }
        GdiplusShutdown(token);
        return saved;
    }

    std::vector<std::uint8_t> protectSecret(const std::string& plain) const override {
        DATA_BLOB input{};
        input.pbData = reinterpret_cast<BYTE*>(const_cast<char*>(plain.data()));
        input.cbData = static_cast<DWORD>(plain.size());

        DATA_BLOB output{};
        if (!CryptProtectData(&input, L"ManagementControl", nullptr, nullptr, nullptr,
                              CRYPTPROTECT_UI_FORBIDDEN, &output)) {
            return {};
        }
        std::vector<std::uint8_t> result(output.pbData, output.pbData + output.cbData);
        LocalFree(output.pbData);
        return result;
    }

    bool unprotectSecret(const std::vector<std::uint8_t>& blob, std::string* out) const override {
        if (!out || blob.empty()) return false;

        DATA_BLOB input{};
        input.pbData = const_cast<uint8_t*>(blob.data());
        input.cbData = static_cast<DWORD>(blob.size());

        DATA_BLOB output{};
        if (!CryptUnprotectData(&input, nullptr, nullptr, nullptr, nullptr,
                                CRYPTPROTECT_UI_FORBIDDEN, &output)) {
            return false;
        }
        out->assign(reinterpret_cast<const char*>(output.pbData), output.cbData);
        LocalFree(output.pbData);
        return true;
    }

    bool installTamperProtection(const std::string& executablePath) override {
        if (executablePath.empty()) return false;

        // Плановий службовий запуск із найвищими правами: агент піднімається разом із системою
        // і не дає видалити себе звичайному користувачеві.
        std::wstring command = L"schtasks.exe /Create /TN \"ManagementControlAgent\" /SC ONLOGON ";
        command += L"/TR \"\\\"" + widen(executablePath) + L"\\\"\" /RL HIGHEST /F";

        std::wstring mutableCommand = command;
        STARTUPINFOW startup{};
        startup.cb = sizeof(startup);
        PROCESS_INFORMATION process{};

        if (!CreateProcessW(nullptr, mutableCommand.data(), nullptr, nullptr, FALSE,
                            CREATE_NO_WINDOW, nullptr, nullptr, &startup, &process)) {
            return false;
        }
        WaitForSingleObject(process.hProcess, 10000);
        CloseHandle(process.hThread);
        CloseHandle(process.hProcess);
        return true;
    }

    void showMessage(const std::string& title, const std::string& text) override {
        MessageBoxA(nullptr, text.c_str(), title.c_str(), MB_OK | MB_ICONINFORMATION);
    }
};

}  // namespace

std::unique_ptr<Platform> createPlatform() {
    return std::make_unique<WindowsPlatform>();
}

}  // namespace mc

#endif  // _WIN32
