//
// Created by eszdman on 22.11.2026.
//

#include "PostSettingsLayout.inc"
#include <algorithm>
#include <initializer_list>
#include <locale>
#include <sstream>
#include <stdexcept>
#include <string_view>
#include <type_traits>

#include "PostNodeReaders.inc"

namespace photon::post {
namespace {
template<class Group, class Read>
auto readFields(Group& group, Read&& read) -> decltype(group.readFields(read)) {
    group.readFields(read);
}

// Only the generated field groups are staged. Shot buffers stay in place on reload.
template<class Group>
Group readGroup(const PostPipelineSettings::Preferences& preferences) {
    Group group;
    readFields(group, [&](const char* key, auto& field, std::initializer_list<std::string_view> choices = {}) {
        const auto found = preferences.find(key);
        if (found == preferences.end()) return;
        const auto& text = found->second;
        using T = std::decay_t<decltype(field)>;
        if constexpr (std::is_same_v<T, std::string>) {
            if (text.find_first_not_of(" \t\r\n") != std::string::npos)
                field = choices.size() && std::find(choices.begin(), choices.end(), text) == choices.end()
                            ? std::string(*choices.begin()) : text;
        } else if constexpr (std::is_enum_v<T>) {
            if (text.find_first_not_of(" \t\r\n") == std::string::npos) return;
            const auto selected = std::find(choices.begin(), choices.end(), text);
            field = static_cast<T>(selected == choices.end() ? 0 : selected - choices.begin());
        } else if constexpr (std::is_same_v<T, bool>) {
            if (text == "1" || text == "true") field = true;
            else if (text == "0" || text == "false") field = false;
            else throw std::invalid_argument(key);
        } else {
            std::istringstream stream(text);
            stream.imbue(std::locale::classic());
            if (!(stream >> field) || !(stream >> std::ws).eof())
                throw std::invalid_argument(key);
        }
    });
    return group;
}
} // namespace

void PostPipelineSettings::loadTunables(const Preferences& preferences, int sensorMode, int alignmentMode) {
    auto groups = settingsLayout(*this);
    std::apply([&](auto&... group) {
        groups = std::make_tuple(readGroup<std::decay_t<decltype(group)>>(preferences)...);
    }, groups);
    if (sensorMode == -2)
        linear.demosaicing = Demosaicing::quad;
    else if (sensorMode == 4)
        linear.demosaicing = Demosaicing::monochrome;
    else if (alignmentMode == 2)
        linear.demosaicing = Demosaicing::mergedRgb;
    else
        linear.demosaicing = graph.demosaicingMethod == 0 ? Demosaicing::legacy : Demosaicing::amaze;

    if (graph.tonePipeline == "off")
        tone.mode = ToneMode::off;
    else if (graph.tonePipeline == "sky")
        tone.mode = ToneMode::sky;
    else if (graph.tonePipeline == "new")
        tone.mode = ToneMode::modern;
    else
        tone.mode = ToneMode::curve;
    display.unsharp = graph.sharpeningType == "unsharp";
    deconvolution = !display.unsharp && graph.sharpeningType != "off";
    display.cropSharpenScale = graph.upscaleSharpenScale;
}

} // namespace photon::post
