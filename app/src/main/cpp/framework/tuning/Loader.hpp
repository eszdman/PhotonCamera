//
// Created by eszdman on 20.11.2026.
//

#pragma once
#include <array>
#include <cstddef>
#include <istream>
#include <algorithm>
#include <cmath>
#include <initializer_list>
#include <optional>
#include <stdexcept>
#include <string>
#include <string_view>
#include <type_traits>

namespace photon::tuning {
// Generated specializations visit the annotated members of a group in wire order.
template<class Group> struct Schema;

namespace detail {
using Choices = std::initializer_list<std::string_view>;

template<class T>
void validate(const char* name, double value, double low, double high) {
    if constexpr (std::is_same_v<T, float>) {
        // Android sends float preferences widened to double; accept the stored endpoint.
        low = std::min(low, static_cast<double>(static_cast<float>(low)));
        high = std::max(high, static_cast<double>(static_cast<float>(high)));
    }
    if (!std::isfinite(value) || value < low || value > high ||
        (!std::is_floating_point_v<T> && value != std::trunc(value)))
        throw std::invalid_argument(std::string("Invalid tunable value for ") + name);
}

inline std::string trim(const std::string& text) {
    auto first = text.find_first_not_of(" \t\r");
    if (first == std::string::npos) return {};
    return text.substr(first, text.find_last_not_of(" \t\r") - first + 1);
}
// Validate the entire update before touching any live fields.
template<class Group, class ValueAt>
void apply(Group& target, ValueAt valueAt) {
    std::size_t i = 0;
    Schema<Group>::visit(target, [&](const char* name, auto& field, double low, double high, Choices) {
        using T = std::decay_t<decltype(field)>;
        if (auto value = valueAt(i++)) validate<T>(name, *value, low, high);
    });
    i = 0;
    Schema<Group>::visit(target, [&](const char*, auto& field, double, double, Choices) {
        using T = std::decay_t<decltype(field)>;
        if (auto value = valueAt(i++)) {
            if constexpr (std::is_enum_v<T>) field = static_cast<T>(static_cast<int>(*value));
            else field = static_cast<T>(*value);
        }
    });
}
} // namespace detail

template<class Group>
void inject(Group& target, const double* values, std::size_t size) {
    if (!values || size != Schema<Group>::packedSize || values[0] != Schema<Group>::tag)
        throw std::invalid_argument("Incompatible tunables schema");
    detail::apply(target, [values](std::size_t i) { return values + i + 1; });
}

template<class Group, std::size_t N>
void inject(Group& target, const std::array<double, N>& packet) {
    static_assert(N == Schema<Group>::packedSize, "Wrong tunables packet size for this group");
    inject(target, packet.data(), N);
}

template<class Group>
void inject(Group& target, std::istream& input) {
    std::array<std::optional<double>, Schema<Group>::packedSize - 1> updates{};
    std::string line;
    while (std::getline(input, line)) {
        line = detail::trim(line.substr(0, line.find('#')));
        if (line.empty()) continue;
        auto split = line.find('=');
        if (split == std::string::npos) throw std::invalid_argument("Expected name=value: " + line);
        auto key = detail::trim(line.substr(0, split));
        auto text = detail::trim(line.substr(split + 1));
        bool found = false;
        std::size_t i = 0;
        Schema<Group>::visit(target, [&](const char* name, auto& field, double, double, detail::Choices choices) {
            auto& update = updates[i++];
            if (key != name) return;
            if (update) throw std::invalid_argument("Duplicate tunable: " + key);
            found = true;
            double value;
            using T = std::decay_t<decltype(field)>;
            if constexpr (std::is_enum_v<T>) {
                auto it = std::find(choices.begin(), choices.end(), text);
                if (it == choices.end()) throw std::invalid_argument("Invalid choice for " + key);
                value = it - choices.begin();
            } else if (std::is_same_v<T, bool> && (text == "true" || text == "false")) {
                value = text == "true";
            } else {
                std::size_t used = 0;
                value = std::stod(text, &used);
                if (used != text.size()) throw std::invalid_argument("Invalid number for " + key);
            }
            update = value;
        });
        if (!found) throw std::invalid_argument("Unknown tunable: " + key);
    }
    if (!input.eof() || input.bad()) throw std::runtime_error("Error reading tunables");
    detail::apply(target, [&](std::size_t i) { return updates[i]; });
}

template<class Group>
Group unpack(const double* values, std::size_t size) {
    Group result{};
    inject(result, values, size);
    return result;
}

template<class Group>
Group load(std::istream& input, Group result = {}) {
    inject(result, input);
    return result;
}
} // namespace photon::tuning
