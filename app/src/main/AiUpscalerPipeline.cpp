#include <ncnn/net.h>
#include <ncnn/gpu.h>
#include <vector>
#include <algorithm>
#include <android/log.h>

#define LOG_TAG "AiUpscalerPipeline"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

class AiUpscalerPipeline {
public:
    AiUpscalerPipeline() {
        ncnn::create_gpu_instance();
        net.opt.use_vulkan_compute = true;
        // Максимальная оптимизация под Mali-G57 MC2
        net.opt.use_fp16_storage = true;
        net.opt.use_fp16_arithmetic = true;
        net.opt.use_packing_layout = true;
        net.opt.use_int8_inference = false;
    }

    ~AiUpscalerPipeline() {
        net.clear();
        ncnn::destroy_gpu_instance();
    }

    bool loadModel(int mode) {
        // 0 = Fast AI, 1 = Detail, 2 = Ultra Detail
        net.clear();
        net.opt.use_int8_inference = (mode == 0);

        const char* paramPath = mode == 2 ? "realsr-ultra.param" : (mode == 1 ? "realsr-detail.param" : "realsr-fast.param");
        const char* modelPath = mode == 2 ? "realsr-ultra.bin" : (mode == 1 ? "realsr-detail.bin" : "realsr-fast.bin");

        if (net.load_param(paramPath) != 0 || net.load_model(modelPath) != 0) {
            LOGE("Failed to load ncnn model");
            return false;
        }
        return true;
    }

    void runRealSRVulkan(const uint8_t* inRgb, int inW, int inH, uint8_t* outRgb, int targetW, int targetH) {
        // Жесткое ограничение tile-size (например, 256x256) для предотвращения GPU OOM
        const int tileSize = 256;
        const int prepadding = 12;
        int scale = targetW / inW;
        if (scale < 1) scale = 1;

        ncnn::Mat inMat = ncnn::Mat::from_pixels(inRgb, ncnn::Mat::PIXEL_RGB, inW, inH);

        for (int y = 0; y < inH; y += tileSize) {
            for (int x = 0; x < inW; x += tileSize) {
                int curTileW = std::min(tileSize, inW - x);
                int curTileH = std::min(tileSize, inH - y);

                ncnn::Mat tileIn;
                ncnn::copy_make_border(inMat, tileIn,
                                       y == 0 ? 0 : prepadding,
                                       y + curTileH == inH ? 0 : prepadding,
                                       x == 0 ? 0 : prepadding,
                                       x + curTileW == inW ? 0 : prepadding,
                                       ncnn::BORDER_REPLICATE, 0.f);

                ncnn::Mat tileInCrop;
                ncnn::copy_cut_border(tileIn, tileInCrop,
                                      y == 0 ? 0 : prepadding,
                                      y + curTileH == inH ? 0 : prepadding,
                                      x == 0 ? 0 : prepadding,
                                      x + curTileW == inW ? 0 : prepadding);

                ncnn::Extractor ex = net.create_extractor();
                ex.set_vulkan_compute(true);
                ex.input("data", tileInCrop);

                ncnn::Mat tileOut;
                ex.extract("output", tileOut);

                int outX = x * scale;
                int outY = y * scale;

                tileOut.to_pixels(outRgb + (outY * targetW + outX) * 3, ncnn::Mat::PIXEL_RGB, targetW * 3);
            }
        }
    }

private:
    ncnn::Net net;
};