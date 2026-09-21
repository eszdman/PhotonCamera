#include "AiUpscalerPipeline.h"
#include <android/log.h>
#include <cpu.h>
#include <algorithm>

#define LOG_TAG "AiUpscaler"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

AiUpscalerPipeline::AiUpscalerPipeline() {
    ncnn::create_gpu_instance();
    int gpu_index = ncnn::get_default_gpu_index();
    if (gpu_index >= 0) {
        vkdev = ncnn::get_gpu_device(gpu_index);
        net.opt.use_vulkan_compute = true;
        net.set_vulkan_device(vkdev);
        LOGD("Vulkan enabled on GPU %d", gpu_index);
    } else {
        LOGE("No Vulkan GPU found, falling back to CPU");
    }
}

AiUpscalerPipeline::~AiUpscalerPipeline() {
    net.clear();
    ncnn::destroy_gpu_instance();
}

int AiUpscalerPipeline::init(const char* modelPath, int mode) {
    currentMode = mode;
    std::string param = std::string(modelPath) + "/realsr.param";
    std::string model = std::string(modelPath) + "/realsr.bin";

    // Configure based on mode
    if (mode == FAST_AI) {
        net.opt.use_fp16_arithmetic = true;
        net.opt.use_fp16_storage = true;
        net.opt.use_fp16_packed = true;
    } else {
        net.opt.use_fp16_arithmetic = false; // Better precision
    }

    int r1 = net.load_param(param.c_str());
    int r2 = net.load_model(model.c_str());

    if (r1 != 0 || r2 != 0) {
        LOGE("Failed to load ncnn model from %s", modelPath);
        return -1;
    }
    return 0;
}

int AiUpscalerPipeline::process(const float* inBayer, int width, int height, float* outRgb, int targetWidth, int targetHeight) {
    // Mali-G57 MC2 Tiling implementation
    ncnn::Mat in(width, height, (void*)inBayer);
    const int tileSize = 256;
    const int overlap = 16;
    float scale = (float)targetWidth / width;

    for (int y = 0; y < height; y += (tileSize - overlap)) {
        for (int x = 0; x < width; x += (tileSize - overlap)) {
            int w = std::min(tileSize, width - x);
            int h = std::min(tileSize, height - y);

            ncnn::Mat in_tile;
            ncnn::copy_cut_border(in, in_tile, y, height - y - h, x, width - x - w);

            ncnn::Extractor ex = net.create_extractor();
            ex.input("input", in_tile);
            ncnn::Mat out_tile;
            ex.extract("output", out_tile);

            // Stitching
            int outX = static_cast<int>(x * scale);
            int outY = static_cast<int>(y * scale);
            int outW = static_cast<int>(w * scale);
            int outH = static_cast<int>(h * scale);

            for (int ty = 0; ty < outH; ++ty) {
                for (int tx = 0; tx < outW; ++tx) {
                    for (int c = 0; c < 3; ++c) {
                        int outIdx = ((outY + ty) * targetWidth + (outX + tx)) * 3 + c;
                        outRgb[outIdx] = out_tile.channel(c).row(ty)[tx];
                    }
                }
            }
        }
    }
    return 0;
}
