//
// Created by eszdman on 21.11.2026.
//

#pragma once

#include "KomputeNode.hpp"
#include <memory>
#include <utility>
#include <vector>

namespace photon::esd4d {

template <typename State>
class KomputePipeline {
  public:
    template <typename Node, typename... Args>
    void add(Args&&... args) {
        nodes_.push_back(std::make_unique<Node>(std::forward<Args>(args)...));
    }

    // Keep the shot's configured node alive until the graph has finished.
    template <typename Node>
    void add(const Node& configured) {
        struct Entry final : KomputeNode<State> {
            explicit Entry(const Node& node) : node(node) {}
            void run(State& state) override { node.run(state); }
            Node node;
        };
        nodes_.push_back(std::make_unique<Entry>(configured));
    }

    void runAll(State& state) {
        for (auto& node : nodes_) node->run(state);
    }

  private:
    std::vector<std::unique_ptr<KomputeNode<State>>> nodes_;
};

} // namespace photon::esd4d
