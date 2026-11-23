//
// Created by eszdman on 19.11.2026.
//

#pragma once

#include <cstddef>
#include <filesystem>

namespace photon::esd4d {
// Creates device pipelines without dispatching work. Returns the number prepared.
// Use an application-private writable directory and call off the UI thread.
std::size_t preparePipelines(const std::filesystem::path &directory);
} // namespace photon::esd4d
