//
// Created by eszdman on 19.11.2026.
//

#include "esd4d/Burst.hpp"
#include "esd4d/Tunables.hpp"
#include "framework/esd4d/ComputeContext.hpp"
#include <cstring>
#include <jni.h>
#include <stdexcept>

using namespace photon::esd4d;

namespace {
// This handle exists only during the synchronous Java collect() callback.
struct Capture {
    BurstOptions options;
    std::vector<BurstFrame> frames;
};
Capture& capture(jlong handle) {
    if (!handle)
        throw std::invalid_argument("Missing burst");
    return *reinterpret_cast<Capture*>(handle);
}

void checkJava(JNIEnv* env) {
    if (env->ExceptionCheck())
        throw std::runtime_error("Camera callback failed");
}
void report(JNIEnv* env, const std::exception& error) {
    if (!env->ExceptionCheck())
        env->ThrowNew(env->FindClass("java/lang/IllegalStateException"), error.what());
}
void* buffer(JNIEnv* env, jobject object, std::size_t bytes) {
    if (object && env->GetDirectBufferCapacity(object) >= static_cast<jlong>(bytes)) {
        if (void* address = env->GetDirectBufferAddress(object))
            return address;
    }
    throw std::invalid_argument("Invalid direct camera buffer");
}
} // namespace

extern "C" JNIEXPORT void JNICALL Java_com_particlesdevs_photoncamera_processing_processor_KomputeBurst_add(
    JNIEnv* env, jclass, jlong handle, jobject raw, jint bits, jboolean fp16, jfloat exposure, jint iso,
    jint x, jint y, jboolean binned, jfloatArray matrix) {
    try {
        auto& shot = capture(handle);
        auto& sensor = shot.options.sensor;
        const auto count = size_t(sensor.width) * sensor.height;
        BurstFrame frame;
        frame.layerMultiplier = exposure;
        frame.iso = iso;
        frame.cropOrigin = {x, y};
        frame.binned = binned;
        if (matrix) {
            frame.homography.emplace();
            env->GetFloatArrayRegion(matrix, 0, 9, frame.homography->data());
            checkJava(env);
        }
        if (bits) {
            if (bits < 1 || bits > 16 || fp16)
                throw std::invalid_argument("Invalid packed RAW format");
            size_t bytes = (uint64_t(count) * bits + 7) / 8;
            frame.pixels =
                unpackSensorBits(sensor, static_cast<uint8_t*>(buffer(env, raw, bytes)), bytes, bits);
        } else {
            auto* data = buffer(env, raw, count * 2);
            frame.pixels.resize(count);
            std::memcpy(frame.pixels.data(), data, count * 2);
            if (fp16)
                frame.format = SampleFormat::NormalizedF16;
        }
        shot.frames.push_back(std::move(frame));
    } catch (const std::exception& e) {
        report(env, e);
    }
}

extern "C" JNIEXPORT void JNICALL Java_com_particlesdevs_photoncamera_processing_processor_KomputeBurst_mergeWithTunables(
    JNIEnv* env, jobject self, jint w, jint h, jint cfa, jint white, jfloatArray black, jdouble s, jdouble o,
    jint analogIso, jboolean binned, jfloat strength, jdoubleArray tunables, jint gainWidth, jint gainHeight,
    jfloatArray gains, jstring cache) {
    try {
        Capture shot;
        auto& opt = shot.options;
        opt.sensor = {w, h, cfa, {}, float(white)};
        if (!black || !cache)
            throw std::invalid_argument("Missing capture metadata");
        env->GetFloatArrayRegion(black, 0, 4, opt.sensor.black.data());
        checkJava(env);
        auto geometry = geometryFor(opt.sensor);
        if (int64_t(w) * h * 2 > INT32_MAX ||
            int64_t(geometry.kernelWidth) * geometry.kernelHeight * 8 > INT32_MAX)
            throw std::invalid_argument("Burst exceeds Java buffer size");
        opt.binned = binned;
        opt.analogIso = analogIso;
        opt.sensorNoise = {s, o};
        opt.mergeStrength = strength;
        std::array<double, Esd4dSchema::packedSize> values;
        if (!tunables || env->GetArrayLength(tunables) != static_cast<jsize>(values.size()))
            throw std::invalid_argument("Wrong ESD4D tunables array size");
        env->GetDoubleArrayRegion(tunables, 0, values.size(), values.data());
        checkJava(env);
        photon::tuning::inject(opt.tunables, values);
        if (gains && gainWidth > 0 && gainHeight > 0 &&
            int64_t(gainWidth) * gainHeight == env->GetArrayLength(gains) / 4) {
            opt.pyramid.gainWidth = gainWidth;
            opt.pyramid.gainHeight = gainHeight;
            std::vector<float> rgba(env->GetArrayLength(gains));
            env->GetFloatArrayRegion(gains, 0, rgba.size(), rgba.data());
            checkJava(env);
            opt.pyramid.gains.resize(size_t(gainWidth) * gainHeight);
            for (size_t i = 0; i < opt.pyramid.gains.size(); ++i)
                opt.pyramid.gains[i] =
                    (rgba[4 * i] + rgba[4 * i + 1] + rgba[4 * i + 2] + rgba[4 * i + 3]) * .25f;
        }
        auto path = env->GetStringUTFChars(cache, nullptr);
        if (!path)
            return;
        try {
            opt.pipelineCacheDirectory = path;
        } catch (...) {
            env->ReleaseStringUTFChars(cache, path);
            throw;
        }
        env->ReleaseStringUTFChars(cache, path);
        // Determine device support while the GLES fallback still owns all input frames.
        auto ctx = std::make_shared<ComputeContext>(opt.pipelineCacheDirectory);
        ctx->checkMergeLimits(geometry);
        auto cls = env->GetObjectClass(self);
        auto method = [&](const char* name, const char* signature) {
            auto id = env->GetMethodID(cls, name, signature);
            checkJava(env);
            return id;
        };
        auto collect = method("collect", "(J)V");
        auto calibration = method("calibration", "(ZDD)[D");
        auto bayer = method("allocateBayer", "(I)Ljava/nio/ByteBuffer;");
        auto kernels = method("allocateKernels", "(II)Ljava/nio/ByteBuffer;");
        auto noise = method("appliedNoise", "(DD)V");
        env->CallVoidMethod(self, collect, reinterpret_cast<jlong>(&shot));
        checkJava(env);
        auto estimate = [&](bool commit, NoiseModel n) -> std::optional<NoiseEstimate> {
            auto values = static_cast<jdoubleArray>(
                env->CallObjectMethod(self, calibration, static_cast<jboolean>(commit), n.s, n.o));
            checkJava(env);
            if (!values)
                return {};
            double v[3];
            env->GetDoubleArrayRegion(values, 0, 3, v);
            checkJava(env);
            return NoiseEstimate{{v[0], v[1]}, v[2] != 0};
        };
        if (opt.tunables.enableAdaptiveNoise && opt.tunables.enableNoiseStore) {
            opt.calibration.prior = estimate(false, {});
            opt.calibration.commit = [&](NoiseModel n) { return estimate(true, n); };
        }
        auto result = processBurst(opt, std::move(shot.frames), std::move(ctx));
        size_t bytes = result.normalizedBayer.size() * 2;
        auto output = env->CallObjectMethod(self, bayer, static_cast<jint>(bytes));
        checkJava(env);
        std::memcpy(buffer(env, output, bytes), result.normalizedBayer.data(), bytes);
        auto map = env->CallObjectMethod(self, kernels, geometry.kernelWidth, geometry.kernelHeight);
        checkJava(env);
        bytes = result.kernelMap.size() * sizeof(uint32_t);
        std::memcpy(buffer(env, map, bytes), result.kernelMap.data(), bytes);
        if (opt.tunables.enableAdaptiveNoise)
            env->CallVoidMethod(self, noise, result.appliedNoise.s, result.appliedNoise.o);
    } catch (const std::exception& e) {
        report(env, e);
    }
}
