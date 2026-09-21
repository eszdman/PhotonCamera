#ifndef AI_UPSCALER_PIPELINE_H
#define AI_UPSCALER_PIPELINE_H

#include <string>
#include <vector>
#include <net.h>
#include <gpu.h>

class AiUpscalerPipeline {
public:
    enum Mode {
        FAST_AI = 0,
        DETAIL = 1,
        ULTRA_DETAIL = 2
    };

    AiUpscalerPipeline();
    ~AiUpscalerPipeline();

    int init(const char* modelPath, int mode);
    int process(const float* inBayer, int width, int height, float* outRgb, int targetWidth, int targetHeight);

private:
    ncnn::Net net;
    ncnn::VulkanDevice* vkdev = nullptr;
    int currentMode = 0;

    // Tiling logic for Mali-G57 MC2 OOM avoidance
    int processTile(const ncnn::Mat& in, ncnn::Mat& out);
};

#endif
