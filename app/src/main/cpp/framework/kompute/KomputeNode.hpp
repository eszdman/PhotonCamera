//
// Created by eszdman on 21.11.2026.
//

#pragma once

namespace photon::esd4d {

template <typename State>
class KomputeNode {
  public:
    virtual ~KomputeNode() = default;
    virtual void run(State& state) = 0;
};

} // namespace photon::esd4d
