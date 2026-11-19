// SPDX-License-Identifier: Apache-2.0
#ifndef CRAW_CODEC_NAMES_H
#define CRAW_CODEC_NAMES_H
#include <cstdint>
namespace craw {
// Display only: never use these labels to select a decoder or rewrite metadata.
// IDs and historical names (Fast, Live v1, Live v2, Compact) remain compatible.
constexpr const char* codecDisplayName(uint8_t codec) {
    switch (codec) {
        case 1: return "CRAW Balanced";
        case 2: return "CRAW Live v1 — Legacy";
        case 3: return "CRAW Live";
        case 4: return "CRAW Compact — Offline";
        default: return "Unknown CRAW codec";
    }
}
} // namespace craw
#endif
