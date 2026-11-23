//
// Created by eszdman on 19.11.2026.
//

#include "Merge.hpp"
#include <algorithm>
#include <iostream>
#include <stdexcept>

int main() try {
    using namespace photon::esd4d;
    // Shifted CFA, partial final workgroups and both masked and interior windows.
    Geometry g{19, 15, 1, 0, 10, 8};
    PackedQuads base(20 * 15 * 2, 0x34003400); // 0.25
    PackedQuads map(10 * 8 * 2);
    for (size_t i = 0; i < map.size(); i += 2) {
        map[i] = 0x3c003c00;     // sigma X/Y = 1
        map[i + 1] = 0x3c000000; // correlation = 0
    }
    RobustMerge burst(g, base, map);
    auto check = [&](std::uint32_t expected) {
        auto output = burst.result();
        if (output.size() != base.size() ||
            !std::all_of(output.begin(), output.end(), [=](auto v) { return v == expected; }))
            throw std::runtime_error("Unexpected accumulator pixels");
    };
    check(0x34003400);
    // High noise floor makes both small residuals fully accepted.
    burst.addAligned(PackedQuads(base.size(), 0x38003800), 0, 16, 1);
    check(0x36003600); // (0.25 + 0.5)/2
    burst.addAligned(PackedQuads(base.size(), 0x00000000), 0, 16, 1);
    check(0x34003400); // accepted mass carried across the second dispatch
    burst.addAligned(PackedQuads(base.size(), 0x3c003c00), 0, 16, 0.5f);
    check(0x34003400); // clipped short exposure rejected

    // All samples stay below the short-exposure clipping limit. Only the exposure
    // counts can distinguish these schedules when the two slots wrap.
    auto scheduledOutput = [&](float shortExposure, float longExposure) {
        RobustMerge scheduled(g, base, map, false);
        scheduled.addAligned(PackedQuads(base.size(), 0x30003000), 0, 16, shortExposure);
        scheduled.addAligned(PackedQuads(base.size(), 0x36003600), 0, 16, longExposure);
        scheduled.addAligned(PackedQuads(base.size(), 0x38003800), 0, 16, shortExposure);
        scheduled.addAligned(PackedQuads(base.size(), 0), 0, 16, longExposure);
        return scheduled.result();
    };
    const auto mixed = scheduledOutput(0.75f, 1.0f);
    if (scheduledOutput(0.949f, 0.95f) != mixed || scheduledOutput(1.0f, 1.0f) == mixed)
        throw std::runtime_error("Scheduled combine changed the short/long exposure weights");
    RobustMerge queued(g, base, map);
    RobustMerge synchronous(g, base, map);
    Warp warp;
    synchronous.addFrame(PackedQuads(base.size(), 0x38003800), warp, 0, 16, 1);
    queued.submitFrame(PackedQuads(base.size(), 0x38003800), warp, 0, 16, 1);
    // Change the pipeline and motion-buffer size while the first submission is in flight.
    warp.sampling = Sampling::Nearest;
    warp.fieldWidth = 2;
    warp.field.resize(8);
    synchronous.addFrame(PackedQuads(base.size(), 0), warp, 0, 16, 1);
    queued.submitFrame(PackedQuads(base.size(), 0), warp, 0, 16, 1);
    // Reuse both slots before requesting a result. The caller can overwrite
    // its input and change the motion field as soon as submission returns.
    PackedQuads scratch(base.size());
    warp.fieldWidth = 1;
    warp.field.resize(4);
    for (unsigned i = 0; i < 5; ++i) {
        for (size_t j = 0; j < scratch.size(); ++j)
            scratch[j] = (i + j) % 3 ? 0x30003500 : 0x35003000;
        warp.sampling = i % 2 ? Sampling::Floor : Sampling::Nearest;
        warp.field[0] = i % 2 ? 0.f : 1.f / g.rawHalfWidth;
        const float exposure = i % 3 ? 1.f : 0.5f;
        synchronous.addFrame(scratch, warp, 0, 16, exposure);
        queued.submitFrame(scratch, warp, 0, 16, exposure);
    }
    if (queued.result() != synchronous.result())
        throw std::runtime_error("Wrapped upload slots changed the merge");
    queued.submitFrame(base, warp, 0, 16, 1);
    synchronous.addFrame(base, warp, 0, 16, 1);
    queued.addAligned(PackedQuads(base.size(), 0x3c003c00), 0, 16, 0.5f);
    synchronous.addAligned(PackedQuads(base.size(), 0x3c003c00), 0, 16, 0.5f);
    if (queued.result() != synchronous.result())
        throw std::runtime_error("Queued and synchronous merge results differ");
    // A caller can abandon a burst with both slots in flight.
    queued.submitFrame(base, warp, 0, 16, 1);
    queued.submitFrame(base, warp, 0, 16, 1);
    std::cout << "ESD4D robust merge passed\n";
} catch (const std::exception& error) {
    std::cerr << error.what() << '\n';
    return 1;
}
