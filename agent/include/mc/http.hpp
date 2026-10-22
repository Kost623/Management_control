#pragma once

// HTTP-клієнт агента на базі libcurl (працює і на Windows, і на Linux).

#include <cstdint>
#include <string>
#include <utility>
#include <vector>

namespace mc {

using QueryParams = std::vector<std::pair<std::string, std::string>>;

struct HttpResponse {
    long status = 0;
    std::string body;

    bool ok() const { return status >= 200 && status < 300; }
    bool unauthorized() const { return status == 401 || status == 403; }
};

class HttpClient {
public:
    HttpClient();
    ~HttpClient();

    HttpClient(const HttpClient&) = delete;
    HttpClient& operator=(const HttpClient&) = delete;

    /// Базова адреса сервера, наприклад "https://mc.example.com" (без слеша в кінці).
    void setBaseUrl(std::string url);
    void setBearerToken(std::string token);
    void setTimeoutSeconds(long seconds);
    /// Перевірка TLS-сертифіката сервера.
    void setVerifyPeer(bool verify);

    HttpResponse get(const std::string& path, const QueryParams& params = {});
    HttpResponse postJson(const std::string& path, const std::string& body);
    HttpResponse postMultipart(const std::string& path,
                               const std::string& fileField,
                               const std::string& fileName,
                               const std::vector<std::uint8_t>& fileData,
                               const QueryParams& fields = {});

    /// Текст останньої помилки транспорту (порожній рядок, якщо помилок не було).
    const std::string& lastError() const { return lastError_; }

    static std::string urlEncode(const std::string& value);

private:
    struct Impl;
    Impl* impl_;
    std::string baseUrl_;
    std::string token_;
    long timeoutSeconds_ = 30;
    bool verifyPeer_ = true;
    std::string lastError_;
};

}  // namespace mc
