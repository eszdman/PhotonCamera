//
// Created by eszdman on 21.11.2026.
//

#pragma once

#include "BurstAlignment.hpp"
#include <stdexcept>
#include <utility>

namespace photon::esd4d {

struct BurstState {
    const BurstOptions& options;
    std::vector<BurstFrame>& frames;
    std::shared_ptr<ComputeContext> context;
    BurstResult result{};
    float referenceExposure = 1;
    PackedQuads reference;
    std::optional<BurstAlignment> alignment;

    PackedQuads takePackedReference() { return std::exchange(reference, {}); }

    BurstAlignment& prepareAlignmentForMerge() {
        if (!alignment) throw std::logic_error("MergeFrames requires StartAlignment");
        alignment->prepareForMerge(*context);
        return *alignment;
    }

    template <typename Node>
    const typename Node::Settings& settings() const { return options.tunables; }
};

} // namespace photon::esd4d
