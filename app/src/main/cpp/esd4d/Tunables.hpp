//
// Created by eszdman on 20.11.2026.
//

#pragma once
#include "tuning/Esd4dTunables.hpp"
#include "framework/tuning/Loader.hpp"
#include "Esd4dBindings.inc"

namespace photon::esd4d {
using Esd4dSchema = tuning::Schema<Esd4dTunables>;

inline Esd4dTunables unpackTunables(const double* values, std::size_t size) {
    return tuning::unpack<Esd4dTunables>(values, size);
}

inline Esd4dTunables loadTunables(std::istream& input, Esd4dTunables defaults = {}) {
    return tuning::load(input, defaults);
}
} // namespace photon::esd4d
