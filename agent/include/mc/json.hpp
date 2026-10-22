#pragma once

// Мінімальний JSON: серіалізація та розбір. Без зовнішніх залежностей.

#include <cstdint>
#include <map>
#include <memory>
#include <optional>
#include <string>
#include <vector>

namespace mc {

class Json;

using JsonArray = std::vector<Json>;
using JsonObject = std::map<std::string, Json>;

/// Значення JSON: null, bool, number, string, array або об'єкт.
class Json {
public:
    enum class Type { Null, Bool, Number, String, Array, Object };

    Json() = default;
    Json(std::nullptr_t) {}
    Json(bool value) : type_(Type::Bool), bool_(value) {}
    Json(double value) : type_(Type::Number), number_(value) {}
    Json(int value) : type_(Type::Number), number_(static_cast<double>(value)) {}
    Json(long long value) : type_(Type::Number), number_(static_cast<double>(value)) {}
    Json(std::string value) : type_(Type::String), string_(std::move(value)) {}
    Json(const char* value) : type_(Type::String), string_(value) {}
    Json(JsonArray value) : type_(Type::Array), array_(std::move(value)) {}
    Json(JsonObject value) : type_(Type::Object), object_(std::move(value)) {}

    static Json array() { return Json(JsonArray{}); }
    static Json object() { return Json(JsonObject{}); }

    Type type() const { return type_; }
    bool isNull() const { return type_ == Type::Null; }
    bool isBool() const { return type_ == Type::Bool; }
    bool isNumber() const { return type_ == Type::Number; }
    bool isString() const { return type_ == Type::String; }
    bool isArray() const { return type_ == Type::Array; }
    bool isObject() const { return type_ == Type::Object; }

    bool asBool(bool fallback = false) const { return isBool() ? bool_ : fallback; }
    double asNumber(double fallback = 0.0) const { return isNumber() ? number_ : fallback; }
    int64_t asInt(int64_t fallback = 0) const;
    std::string asString(const std::string& fallback = "") const {
        return isString() ? string_ : fallback;
    }

    /// Повертає значення за ключем об'єкта (якщо тип не Object — пустий optional).
    std::optional<Json> get(const std::string& key) const;
    /// Повертає значення за індексом масиву.
    std::optional<Json> at(size_t index) const;

    /// Додає або замінює поле об'єкта. Якщо це не об'єкт — перетворює на об'єкт.
    void set(const std::string& key, Json value);
    /// Додає елемент у масив. Якщо це не масив — перетворює на масив.
    void push(Json value);

    const JsonArray& items() const { return array_; }
    const JsonObject& fields() const { return object_; }

    /// Серіалізація в рядок.
    std::string dump() const;

    /// Розбір рядка. Повертає null-Json у разі помилки.
    static Json parse(const std::string& text, std::string* error = nullptr);

private:
    void dumpTo(std::string& out) const;

    Type type_ = Type::Null;
    bool bool_ = false;
    double number_ = 0.0;
    std::string string_;
    JsonArray array_;
    JsonObject object_;
};

/// Екранування рядка для вставки у JSON.
std::string escapeJsonString(const std::string& value);

}  // namespace mc
