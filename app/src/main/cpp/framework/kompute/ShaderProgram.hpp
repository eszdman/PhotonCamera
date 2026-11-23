//
// Created by eszdman on 22.11.2026.
//

#pragma once
#include <tuple>
#include <type_traits>
#include <utility>

namespace photon::gpu {
// Backends record into an existing command sequence. Submission belongs to its owner.
template <class Shader, class Recorder, class... Inputs>
class BoundProgram {
  public:
    BoundProgram(Recorder recorder, const typename Shader::Parameters& vars, Inputs... inputs)
        : recorder_(recorder), vars_(vars), inputs_(std::move(inputs)...) {}

    template <class Output> void writeTo(const Output& output) const {
        std::apply([&](const auto&... inputs) {
            recorder_.template record<Shader>(vars_, output, inputs...);
        }, inputs_);
    }

  private:
    Recorder recorder_;
    typename Shader::Parameters vars_;
    std::tuple<Inputs...> inputs_;
};

template <class Shader, class Recorder>
class ShaderProgram {
  public:
    explicit ShaderProgram(Recorder recorder) : recorder_(recorder) {}
    void setVars(const typename Shader::Parameters& values) { vars_ = values; }
    template <class T> void setVar(T Shader::Parameters::*field, T value) { vars_.*field = value; }

    template <class... Inputs> [[nodiscard]] auto bindInputs(Inputs... inputs) const {
        static_assert((std::is_same_v<Shader, typename Inputs::Port::Owner> && ...),
                      "Input belongs to another shader");
        return BoundProgram<Shader, Recorder, Inputs...>(recorder_, vars_, std::move(inputs)...);
    }
  private:
    Recorder recorder_;
    typename Shader::Parameters vars_{};
};
}
