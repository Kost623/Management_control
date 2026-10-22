#include "mc/http.hpp"

#include <curl/curl.h>

#include <memory>

namespace mc {
namespace {

struct CurlGlobal {
    CurlGlobal() { curl_global_init(CURL_GLOBAL_DEFAULT); }
    ~CurlGlobal() { curl_global_cleanup(); }
};

void ensureCurlGlobal() {
    static CurlGlobal instance;
    (void)instance;
}

size_t appendToString(char* ptr, size_t size, size_t count, void* userdata) {
    auto* out = static_cast<std::string*>(userdata);
    out->append(ptr, size * count);
    return size * count;
}

std::string joinUrl(const std::string& base, const std::string& path) {
    if (base.empty()) return path;
    if (path.empty()) return base;
    if (base.back() == '/' && path.front() == '/') return base + path.substr(1);
    if (base.back() != '/' && path.front() != '/') return base + "/" + path;
    return base + path;
}

}  // namespace

struct HttpClient::Impl {
    CURL* handle = nullptr;

    ~Impl() {
        if (handle) curl_easy_cleanup(handle);
    }
};

HttpClient::HttpClient() : impl_(new Impl()) {
    ensureCurlGlobal();
    impl_->handle = curl_easy_init();
}

HttpClient::~HttpClient() {
    delete impl_;
}

void HttpClient::setBaseUrl(std::string url) {
    while (!url.empty() && url.back() == '/') url.pop_back();
    baseUrl_ = std::move(url);
}

void HttpClient::setBearerToken(std::string token) {
    token_ = std::move(token);
}

void HttpClient::setTimeoutSeconds(long seconds) {
    timeoutSeconds_ = seconds > 0 ? seconds : 1;
}

void HttpClient::setVerifyPeer(bool verify) {
    verifyPeer_ = verify;
}

std::string HttpClient::urlEncode(const std::string& value) {
    char* raw = curl_easy_escape(nullptr, value.c_str(), static_cast<int>(value.size()));
    if (!raw) return {};
    std::string encoded(raw);
    curl_free(raw);
    return encoded;
}

HttpResponse HttpClient::get(const std::string& path, const QueryParams& params) {
    HttpResponse response;
    if (!impl_->handle) {
        lastError_ = "HTTP-клієнт не ініціалізовано";
        return response;
    }

    std::string url = joinUrl(baseUrl_, path);
    if (!params.empty()) {
        url += (url.find('?') == std::string::npos) ? "?" : "&";
        bool first = true;
        for (const auto& [key, value] : params) {
            if (!first) url += "&";
            first = false;
            url += urlEncode(key) + "=" + urlEncode(value);
        }
    }

    CURL* curl = impl_->handle;
    curl_easy_reset(curl);
    curl_easy_setopt(curl, CURLOPT_URL, url.c_str());
    curl_easy_setopt(curl, CURLOPT_HTTPGET, 1L);
    curl_easy_setopt(curl, CURLOPT_WRITEFUNCTION, appendToString);
    curl_easy_setopt(curl, CURLOPT_WRITEDATA, &response.body);
    curl_easy_setopt(curl, CURLOPT_TIMEOUT, timeoutSeconds_);
    curl_easy_setopt(curl, CURLOPT_CONNECTTIMEOUT, std::min<long>(timeoutSeconds_, 15L));
    curl_easy_setopt(curl, CURLOPT_FOLLOWLOCATION, 1L);
    curl_easy_setopt(curl, CURLOPT_MAXREDIRS, 3L);
    curl_easy_setopt(curl, CURLOPT_SSL_VERIFYPEER, verifyPeer_ ? 1L : 0L);
    curl_easy_setopt(curl, CURLOPT_SSL_VERIFYHOST, verifyPeer_ ? 2L : 0L);
    curl_easy_setopt(curl, CURLOPT_NOSIGNAL, 1L);
    if (!token_.empty()) {
        std::string header = "Authorization: Bearer " + token_;
        curl_slist* headers = curl_slist_append(nullptr, header.c_str());
        curl_easy_setopt(curl, CURLOPT_HTTPHEADER, headers);
        curl_slist_free_all(headers);
    }

    CURLcode code = curl_easy_perform(curl);
    if (code != CURLE_OK) {
        lastError_ = curl_easy_strerror(code);
        response.status = 0;
        return response;
    }
    lastError_.clear();
    curl_easy_getinfo(curl, CURLINFO_RESPONSE_CODE, &response.status);
    return response;
}

HttpResponse HttpClient::postJson(const std::string& path, const std::string& body) {
    HttpResponse response;
    if (!impl_->handle) {
        lastError_ = "HTTP-клієнт не ініціалізовано";
        return response;
    }

    CURL* curl = impl_->handle;
    curl_easy_reset(curl);
    curl_easy_setopt(curl, CURLOPT_URL, joinUrl(baseUrl_, path).c_str());
    curl_easy_setopt(curl, CURLOPT_POST, 1L);
    curl_easy_setopt(curl, CURLOPT_POSTFIELDS, body.c_str());
    curl_easy_setopt(curl, CURLOPT_POSTFIELDSIZE, static_cast<long>(body.size()));
    curl_easy_setopt(curl, CURLOPT_WRITEFUNCTION, appendToString);
    curl_easy_setopt(curl, CURLOPT_WRITEDATA, &response.body);
    curl_easy_setopt(curl, CURLOPT_TIMEOUT, timeoutSeconds_);
    curl_easy_setopt(curl, CURLOPT_CONNECTTIMEOUT, std::min<long>(timeoutSeconds_, 15L));
    curl_easy_setopt(curl, CURLOPT_FOLLOWLOCATION, 1L);
    curl_easy_setopt(curl, CURLOPT_SSL_VERIFYPEER, verifyPeer_ ? 1L : 0L);
    curl_easy_setopt(curl, CURLOPT_SSL_VERIFYHOST, verifyPeer_ ? 2L : 0L);
    curl_easy_setopt(curl, CURLOPT_NOSIGNAL, 1L);

    curl_slist* headers = nullptr;
    headers = curl_slist_append(headers, "Content-Type: application/json");
    headers = curl_slist_append(headers, "Expect:");  // не чекати 100-continue
    if (!token_.empty()) {
        headers = curl_slist_append(headers, ("Authorization: Bearer " + token_).c_str());
    }
    curl_easy_setopt(curl, CURLOPT_HTTPHEADER, headers);

    CURLcode code = curl_easy_perform(curl);
    curl_slist_free_all(headers);
    if (code != CURLE_OK) {
        lastError_ = curl_easy_strerror(code);
        response.status = 0;
        return response;
    }
    lastError_.clear();
    curl_easy_getinfo(curl, CURLINFO_RESPONSE_CODE, &response.status);
    return response;
}

HttpResponse HttpClient::postMultipart(const std::string& path,
                                       const std::string& fileField,
                                       const std::string& fileName,
                                       const std::vector<std::uint8_t>& fileData,
                                       const QueryParams& fields) {
    HttpResponse response;
    if (!impl_->handle) {
        lastError_ = "HTTP-клієнт не ініціалізовано";
        return response;
    }

    curl_mime* mime = curl_mime_init(impl_->handle);
    curl_mimepart* part = curl_mime_addpart(mime);
    curl_mime_name(part, fileField.c_str());
    // Дані з пам'яті (без тимчасових файлів на пристрої) + ім'я файлу у Content-Disposition.
    curl_mime_data(part, reinterpret_cast<const char*>(fileData.data()), fileData.size());
    curl_mime_filename(part, fileName.c_str());

    for (const auto& [key, value] : fields) {
        curl_mimepart* field = curl_mime_addpart(mime);
        curl_mime_name(field, key.c_str());
        curl_mime_data(field, value.c_str(), static_cast<size_t>(value.size()));
    }

    CURL* curl = impl_->handle;
    curl_easy_reset(curl);
    curl_easy_setopt(curl, CURLOPT_URL, joinUrl(baseUrl_, path).c_str());
    curl_easy_setopt(curl, CURLOPT_POST, 1L);
    curl_easy_setopt(curl, CURLOPT_MIMEPOST, mime);
    curl_easy_setopt(curl, CURLOPT_WRITEFUNCTION, appendToString);
    curl_easy_setopt(curl, CURLOPT_WRITEDATA, &response.body);
    curl_easy_setopt(curl, CURLOPT_TIMEOUT, timeoutSeconds_);
    curl_easy_setopt(curl, CURLOPT_CONNECTTIMEOUT, std::min<long>(timeoutSeconds_, 15L));
    curl_easy_setopt(curl, CURLOPT_SSL_VERIFYPEER, verifyPeer_ ? 1L : 0L);
    curl_easy_setopt(curl, CURLOPT_SSL_VERIFYHOST, verifyPeer_ ? 2L : 0L);
    curl_easy_setopt(curl, CURLOPT_NOSIGNAL, 1L);

    curl_slist* headers = nullptr;
    headers = curl_slist_append(headers, "Expect:");
    if (!token_.empty()) {
        headers = curl_slist_append(headers, ("Authorization: Bearer " + token_).c_str());
    }
    curl_easy_setopt(curl, CURLOPT_HTTPHEADER, headers);

    CURLcode code = curl_easy_perform(curl);
    curl_slist_free_all(headers);
    curl_mime_free(mime);

    if (code != CURLE_OK) {
        lastError_ = curl_easy_strerror(code);
        response.status = 0;
        return response;
    }
    lastError_.clear();
    curl_easy_getinfo(curl, CURLINFO_RESPONSE_CODE, &response.status);
    return response;
}

}  // namespace mc
