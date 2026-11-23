//
// Created by eszdman on 19.11.2026.
//

#include "PipelineCache.hpp"
#include "ComputeContext.hpp"
#include "EsdShaders.hpp"
#include "PostShaderCatalog.hpp"
#include "Shader.hpp"
#include <algorithm>
#include <array>
#include <cstring>
#include <fstream>
#include <kompute/Kompute.hpp>
#include <mutex>
#include <random>
#include <sstream>
#include <stdexcept>

namespace photon::esd4d {
namespace {
struct Layout {
    unsigned storage, uniforms, pushBytes;
};

template <class Shader> constexpr Layout layoutFor() {
    return {Shader::count, unsigned(Shader::uniform), Shader::uniform ? 0u : unsigned(sizeof(typename Shader::Parameters))};
}

struct Variant {
    const char* name;
    Layout layout;
    bool row;
};
constexpr Variant variants[] = {
#define ESD_VARIANT(name, family, row) {name, layoutFor<shaders::family>(), row},
#include "esd4d_shader_catalog.inc"
#undef ESD_VARIANT
};

// One snapshot atomically couples the driver's binary and the pipeline keys it contains.
using Bytes = std::vector<uint8_t>;
struct Snapshot {
    std::vector<uint64_t> keys;
    Bytes driver;
};
uint64_t hashBytes(const void *data, size_t size, uint64_t hash = 14695981039346656037ull) {
    const auto *bytes = static_cast<const uint8_t *>(data);
    for (size_t i = 0; i < size; ++i)
        hash = (hash ^ bytes[i]) * 1099511628211ull;
    return hash;
}

std::filesystem::path cacheFile(const std::filesystem::path &dir, const vk::PhysicalDeviceProperties &p) {
    std::ostringstream name;
    name << "esd4d-v1-" << p.vendorID << '-' << p.deviceID << '-' << p.driverVersion << '-' << sizeof(void *)
         << '-' << std::hex << hashBytes(p.pipelineCacheUUID.data(), VK_UUID_SIZE) << ".bin";
    return dir / name.str();
}

constexpr size_t maxCacheBytes = 64 * 1024 * 1024;
Snapshot readSnapshot(const std::filesystem::path &file, const vk::PhysicalDeviceProperties &p) {
    std::ifstream in(file, std::ios::binary | std::ios::ate);
    const auto length = in.tellg();
    if (length < 12 || length > std::streamoff(maxCacheBytes))
        return {};
    Bytes bytes(static_cast<size_t>(length));
    in.seekg(0);
    if (!in.read(reinterpret_cast<char *>(bytes.data()), length))
        return {};
    uint64_t checksum;
    uint32_t count;
    std::memcpy(&checksum, bytes.data(), 8);
    std::memcpy(&count, bytes.data() + 8, 4);
    if (checksum != hashBytes(bytes.data() + 8, bytes.size() - 8) || count > (bytes.size() - 12) / 8)
        return {};
    const auto offset = 12 + size_t(count) * 8;
    VkPipelineCacheHeaderVersionOne header{};
    if (bytes.size() - offset < sizeof(header))
        return {};
    std::memcpy(&header, bytes.data() + offset, sizeof(header));
    if (header.headerSize != sizeof(header) || header.headerVersion != VK_PIPELINE_CACHE_HEADER_VERSION_ONE ||
        header.vendorID != p.vendorID || header.deviceID != p.deviceID ||
        std::memcmp(header.pipelineCacheUUID, p.pipelineCacheUUID.data(), VK_UUID_SIZE))
        return {};
    Snapshot s;
    s.keys.resize(count);
    if (count)
        std::memcpy(s.keys.data(), bytes.data() + 12, count * 8);
    s.driver.assign(bytes.begin() + offset, bytes.end());
    return s;
}

void saveSnapshot(const std::filesystem::path &file, const Snapshot &s) {
    uint32_t count = static_cast<uint32_t>(s.keys.size());
    Bytes bytes(12 + 8 * s.keys.size() + s.driver.size());
    if (bytes.size() > maxCacheBytes)
        throw std::runtime_error("Pipeline cache exceeds size limit");
    std::memcpy(bytes.data() + 8, &count, 4);
    std::memcpy(bytes.data() + 12, s.keys.data(), 8 * count);
    std::memcpy(bytes.data() + 12 + 8 * count, s.driver.data(), s.driver.size());
    auto checksum = hashBytes(bytes.data() + 8, bytes.size() - 8);
    std::memcpy(bytes.data(), &checksum, 8);
    std::filesystem::create_directories(file.parent_path());
    auto tmp = file;
    tmp += "." + std::to_string(std::random_device{}()) + ".tmp";
    try {
        std::ofstream out;
        out.exceptions(std::ios::failbit | std::ios::badbit);
        out.open(tmp, std::ios::binary | std::ios::trunc);
        out.write(reinterpret_cast<const char *>(bytes.data()), bytes.size());
        out.close();
        std::filesystem::rename(tmp, file);
    } catch (...) {
        std::error_code ignored;
        std::filesystem::remove(tmp, ignored);
        throw;
    }
}

Snapshot initialize(kp::Manager &gpu, const std::filesystem::path &directory) {
    const auto p = gpu.getDeviceProperties();
    Snapshot s;
    if (!directory.empty())
        s = readSnapshot(cacheFile(directory, p), p);
    try {
        gpu.initPipelineCache(s.driver);
    } catch (const vk::SystemError &) {
        if (s.driver.empty())
            throw;
        gpu.initPipelineCache({});
        return {};
    }
    return s;
}
} // namespace

ComputeContext::ComputeContext(const std::filesystem::path &directory)
    : manager_(std::make_unique<kp::Manager>()) {
    initialize(*manager_, directory);
}

std::size_t preparePipelines(const std::filesystem::path &directory) {
    if (directory.empty())
        throw std::invalid_argument("Missing pipeline cache directory");
    static std::mutex preparation;
    const std::lock_guard<std::mutex> lock(preparation);
    kp::Manager gpu;
    auto snapshot = initialize(gpu, directory);
    std::vector<uint64_t> current;
    std::shared_ptr<kp::Tensor> storage, uniform;
    const auto limits = gpu.getDeviceProperties().limits;
    std::size_t prepared = 0;
    auto prepare = [&](const char* name, Layout layout, auto load) {
        if (layout.storage > limits.maxPerStageDescriptorStorageBuffers ||
            layout.storage > limits.maxDescriptorSetStorageBuffers ||
            layout.storage + layout.uniforms > limits.maxPerStageResources ||
            layout.pushBytes > limits.maxPushConstantsSize)
            return;
        auto code = load(name);
        std::array<unsigned, 3> abi{layout.storage, layout.uniforms, layout.pushBytes};
        auto key = hashBytes(abi.data(), sizeof(abi), hashBytes(code.data(), code.size() * sizeof(code[0])));
        if (std::find(current.begin(), current.end(), key) != current.end()) return;
        current.push_back(key);
        if (std::find(snapshot.keys.begin(), snapshot.keys.end(), key) != snapshot.keys.end())
            return;
        if (!storage)
            storage = gpu.tensorT<uint32_t>(1);
        std::vector<std::shared_ptr<kp::Memory>> bindings(layout.storage, storage);
        if (layout.uniforms) {
            uint32_t zero = 0;
            if (!uniform)
                uniform = gpu.tensor(&zero, 1, sizeof(zero), kp::Memory::DataTypes::eCustom,
                                     kp::Memory::MemoryTypes::eDevice, true);
            bindings.push_back(uniform);
        }
        gpu.algorithm<float, uint32_t>(bindings, code, {1, 1, 1}, {},
                                       std::vector<uint32_t>(layout.pushBytes / 4));
        ++prepared;
    };
    const bool useRows = usesRowShaders(gpu);
    for (const auto& variant : variants)
        if (!variant.row || useRows) prepare(variant.name, variant.layout, shader);

    post::forEachPostShader([&](auto pass) {
        using Pass = decltype(pass);
        // Large post parameter blocks are SSBOs, whereas merge uses a UBO.
        constexpr Layout layout{Pass::count + Pass::parameterBuffer, 0,
                                Pass::parameterBuffer ? 0u : unsigned(sizeof(typename Pass::Parameters))};
        prepare(Pass::asset, layout, post::shaderWords);
    });
    if (prepared || current != snapshot.keys) {
        snapshot.keys = std::move(current);
        snapshot.driver = gpu.pipelineCacheData();
        saveSnapshot(cacheFile(directory, gpu.getDeviceProperties()), snapshot);
    }
    return prepared;
}
} // namespace photon::esd4d
