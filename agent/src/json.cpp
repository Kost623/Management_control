#include "mc/json.hpp"

#include <cmath>
#include <cstdio>
#include <cstdlib>
#include <sstream>

namespace mc {
namespace {

struct Parser {
    const std::string& text;
    size_t pos = 0;
    std::string error;

    explicit Parser(const std::string& t) : text(t) {}

    void skipWhitespace() {
        while (pos < text.size()) {
            char c = text[pos];
            if (c == ' ' || c == '\t' || c == '\n' || c == '\r') {
                ++pos;
            } else {
                break;
            }
        }
    }

    bool fail(const std::string& message) {
        if (error.empty()) {
            error = message + " (позиція " + std::to_string(pos) + ")";
        }
        return false;
    }

    bool parseValue(Json& out) {
        skipWhitespace();
        if (pos >= text.size()) {
            return fail("несподіваний кінець входу");
        }
        char c = text[pos];
        switch (c) {
            case '{': return parseObject(out);
            case '[': return parseArray(out);
            case '"': {
                std::string s;
                if (!parseString(s)) return false;
                out = Json(std::move(s));
                return true;
            }
            case 't':
                if (text.compare(pos, 4, "true") == 0) { pos += 4; out = Json(true); return true; }
                return fail("очікувалося true");
            case 'f':
                if (text.compare(pos, 5, "false") == 0) { pos += 5; out = Json(false); return true; }
                return fail("очікувалося false");
            case 'n':
                if (text.compare(pos, 4, "null") == 0) { pos += 4; out = Json(); return true; }
                return fail("очікувалося null");
            default:
                return parseNumber(out);
        }
    }

    bool parseNumber(Json& out) {
        size_t start = pos;
        if (pos < text.size() && (text[pos] == '-' || text[pos] == '+')) ++pos;
        bool hasDigits = false;
        while (pos < text.size() && std::isdigit(static_cast<unsigned char>(text[pos]))) {
            ++pos;
            hasDigits = true;
        }
        if (pos < text.size() && text[pos] == '.') {
            ++pos;
            while (pos < text.size() && std::isdigit(static_cast<unsigned char>(text[pos]))) {
                ++pos;
                hasDigits = true;
            }
        }
        if (hasDigits && pos < text.size() && (text[pos] == 'e' || text[pos] == 'E')) {
            ++pos;
            if (pos < text.size() && (text[pos] == '-' || text[pos] == '+')) ++pos;
            while (pos < text.size() && std::isdigit(static_cast<unsigned char>(text[pos]))) ++pos;
        }
        if (!hasDigits) {
            return fail("очікувалося число");
        }
        out = Json(std::strtod(text.substr(start, pos - start).c_str(), nullptr));
        return true;
    }

    bool parseString(std::string& out) {
        if (pos >= text.size() || text[pos] != '"') {
            return fail("очікувалася лапка");
        }
        ++pos;
        out.clear();
        while (pos < text.size()) {
            char c = text[pos++];
            if (c == '"') {
                return true;
            }
            if (c != '\\') {
                out.push_back(c);
                continue;
            }
            if (pos >= text.size()) {
                return fail("незавершений escape");
            }
            char esc = text[pos++];
            switch (esc) {
                case '"': out.push_back('"'); break;
                case '\\': out.push_back('\\'); break;
                case '/': out.push_back('/'); break;
                case 'b': out.push_back('\b'); break;
                case 'f': out.push_back('\f'); break;
                case 'n': out.push_back('\n'); break;
                case 'r': out.push_back('\r'); break;
                case 't': out.push_back('\t'); break;
                case 'u': {
                    if (pos + 4 > text.size()) {
                        return fail("некоректний \\u escape");
                    }
                    unsigned code = 0;
                    for (int i = 0; i < 4; ++i) {
                        char h = text[pos + i];
                        code <<= 4;
                        if (h >= '0' && h <= '9') code |= static_cast<unsigned>(h - '0');
                        else if (h >= 'a' && h <= 'f') code |= static_cast<unsigned>(h - 'a' + 10);
                        else if (h >= 'A' && h <= 'F') code |= static_cast<unsigned>(h - 'A' + 10);
                        else return fail("некоректна шістнадцяткова цифра");
                    }
                    pos += 4;
                    // UTF-8 кодування
                    if (code < 0x80) {
                        out.push_back(static_cast<char>(code));
                    } else if (code < 0x800) {
                        out.push_back(static_cast<char>(0xC0 | (code >> 6)));
                        out.push_back(static_cast<char>(0x80 | (code & 0x3F)));
                    } else {
                        out.push_back(static_cast<char>(0xE0 | (code >> 12)));
                        out.push_back(static_cast<char>(0x80 | ((code >> 6) & 0x3F)));
                        out.push_back(static_cast<char>(0x80 | (code & 0x3F)));
                    }
                    break;
                }
                default:
                    return fail("невідомий escape");
            }
        }
        return fail("незакрита рядок");
    }

    bool parseArray(Json& out) {
        ++pos;  // '['
        JsonArray items;
        skipWhitespace();
        if (pos < text.size() && text[pos] == ']') {
            ++pos;
            out = Json(std::move(items));
            return true;
        }
        while (true) {
            Json element;
            if (!parseValue(element)) return false;
            items.push_back(std::move(element));
            skipWhitespace();
            if (pos >= text.size()) return fail("незакритий масив");
            if (text[pos] == ',') { ++pos; continue; }
            if (text[pos] == ']') { ++pos; break; }
            return fail("очікувалася ',' або ']'");
        }
        out = Json(std::move(items));
        return true;
    }

    bool parseObject(Json& out) {
        ++pos;  // '{'
        JsonObject fields;
        skipWhitespace();
        if (pos < text.size() && text[pos] == '}') {
            ++pos;
            out = Json(std::move(fields));
            return true;
        }
        while (true) {
            skipWhitespace();
            std::string key;
            if (!parseString(key)) return false;
            skipWhitespace();
            if (pos >= text.size() || text[pos] != ':') return fail("очікувалася ':'");
            ++pos;
            Json value;
            if (!parseValue(value)) return false;
            fields[key] = std::move(value);
            skipWhitespace();
            if (pos >= text.size()) return fail("незакритий об'єкт");
            if (text[pos] == ',') { ++pos; continue; }
            if (text[pos] == '}') { ++pos; break; }
            return fail("очікувалася ',' або '}'");
        }
        out = Json(std::move(fields));
        return true;
    }
};

}  // namespace

int64_t Json::asInt(int64_t fallback) const {
    if (!isNumber()) return fallback;
    return static_cast<int64_t>(number_);
}

std::optional<Json> Json::get(const std::string& key) const {
    if (!isObject()) return std::nullopt;
    auto it = object_.find(key);
    if (it == object_.end()) return std::nullopt;
    return it->second;
}

std::optional<Json> Json::at(size_t index) const {
    if (!isArray() || index >= array_.size()) return std::nullopt;
    return array_[index];
}

void Json::set(const std::string& key, Json value) {
    if (!isObject()) {
        *this = Json::object();
    }
    object_[key] = std::move(value);
}

void Json::push(Json value) {
    if (!isArray()) {
        *this = Json::array();
    }
    array_.push_back(std::move(value));
}

std::string escapeJsonString(const std::string& value) {
    std::string out;
    out.reserve(value.size() + 2);
    for (unsigned char c : value) {
        switch (c) {
            case '"': out += "\\\""; break;
            case '\\': out += "\\\\"; break;
            case '\b': out += "\\b"; break;
            case '\f': out += "\\f"; break;
            case '\n': out += "\\n"; break;
            case '\r': out += "\\r"; break;
            case '\t': out += "\\t"; break;
            default:
                if (c < 0x20) {
                    char buf[8];
                    std::snprintf(buf, sizeof(buf), "\\u%04x", c);
                    out += buf;
                } else {
                    out.push_back(static_cast<char>(c));
                }
        }
    }
    return out;
}

void Json::dumpTo(std::string& out) const {
    switch (type_) {
        case Type::Null:
            out += "null";
            break;
        case Type::Bool:
            out += bool_ ? "true" : "false";
            break;
        case Type::Number: {
            if (std::isfinite(number_)) {
                char buf[32];
                if (number_ == static_cast<double>(static_cast<int64_t>(number_)) &&
                    std::fabs(number_) < 9.2e18) {
                    std::snprintf(buf, sizeof(buf), "%lld", static_cast<long long>(number_));
                } else {
                    std::snprintf(buf, sizeof(buf), "%.10g", number_);
                }
                out += buf;
            } else {
                out += "null";
            }
            break;
        }
        case Type::String:
            out += '"';
            out += escapeJsonString(string_);
            out += '"';
            break;
        case Type::Array: {
            out += '[';
            for (size_t i = 0; i < array_.size(); ++i) {
                if (i) out += ',';
                array_[i].dumpTo(out);
            }
            out += ']';
            break;
        }
        case Type::Object: {
            out += '{';
            bool first = true;
            for (const auto& [key, value] : object_) {
                if (!first) out += ',';
                first = false;
                out += '"';
                out += escapeJsonString(key);
                out += "\":";
                value.dumpTo(out);
            }
            out += '}';
            break;
        }
    }
}

std::string Json::dump() const {
    std::string out;
    dumpTo(out);
    return out;
}

Json Json::parse(const std::string& text, std::string* error) {
    Parser parser(text);
    Json result;
    if (!parser.parseValue(result)) {
        if (error) *error = parser.error;
        return Json();
    }
    parser.skipWhitespace();
    if (parser.pos != text.size()) {
        if (error) *error = "залишкові символи після JSON";
        return Json();
    }
    if (error) error->clear();
    return result;
}

}  // namespace mc
