//
// Created by eszdman on 20.11.2026.
//

#pragma once

namespace photon::tuning { template<class Group> struct Schema; }

// Clang exposes the metadata to the build tool. The fields have their normal
// C++ types, layout and initializers with every target compiler.
#ifdef PHOTON_DISCOVER_TUNABLES
#define TUNABLE_GROUP(name) [[clang::annotate("photon", "photon.group", name)]]
#define TUNABLE(...) [[clang::annotate("photon", "photon.field", __VA_ARGS__)]]
#define TUNABLE_LABEL(label) [[clang::annotate("photon", "photon.label", label)]]
#else
#define TUNABLE_GROUP(name)
#define TUNABLE(...)
#define TUNABLE_LABEL(label)
#endif
