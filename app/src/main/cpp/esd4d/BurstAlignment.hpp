//
// Created by eszdman on 21.11.2026.
//

#pragma once

#include "Burst.hpp"

namespace photon::esd4d {

class BlockAlignment;

struct MatchedFrame {
    BurstFrame frame;
    Warp warp;
};

// Owns the matcher and its schedule for one burst. The options and frames must
// outlive this object, including while the Halide producer is joining.
class BurstAlignment {
  public:
    BurstAlignment(const BurstOptions&, std::vector<BurstFrame>&, NoiseModel);
    ~BurstAlignment();

    void prepareForMerge(ComputeContext&);
    // Returns the next frame in exposure order once the matcher has finished reading it.
    // The consumer owns its pixels and may release them after staging the GPU upload.
    std::optional<MatchedFrame> awaitNextFrame();

  private:
    Warp warpAt(std::size_t position);
    const BurstOptions& options_;
    std::vector<BurstFrame>& frames_;
    NoiseModel noise_;
    AlignerSelect matcher_;
    std::vector<std::size_t> order_;
    std::size_t nextPosition_ = 0;
    std::unique_ptr<BlockAlignment> blocks_;
#ifdef ESD4D_HAS_PRECISE_ALIGNMENT
    class HalideWorker;
    std::unique_ptr<HalideWorker> halide_;
#endif
};

} // namespace photon::esd4d
