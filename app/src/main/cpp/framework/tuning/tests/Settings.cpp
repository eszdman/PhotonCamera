//
// Created by eszdman on 20.11.2026.
//

#include "../Annotations.hpp"

namespace example {
namespace {
enum class Mode { small, large TUNABLE_LABEL("Large") };

struct ExampleNode {
    struct Settings {
        TUNABLE("Test", "Enabled") bool enabled = true;
    };
};

class TUNABLE_GROUP("Settings") Settings : public ExampleNode::Settings {
    friend struct photon::tuning::Schema<Settings>;
    TUNABLE("Test", "Count", "", -8, 8, 1) int count = 2 * 3;
    TUNABLE("Test", "Mode", "") Mode mode = Mode::large;

public:
    int scratch = 0;
    void refresh(const double* values, unsigned size);
    bool matches(int expectedCount) const {
        return !enabled && count == expectedCount && mode == Mode::small && scratch == 42;
    }
};
}
}

// The compiler discovers the declaration before these bindings exist.
#ifndef PHOTON_DISCOVER_TUNABLES
#include "../Loader.hpp"
#include "bindings.inc"

void example::Settings::refresh(const double* values, unsigned size) {
    photon::tuning::inject(*this, values, size);
}
#endif
