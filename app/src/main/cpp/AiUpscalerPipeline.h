#ifndef AI_UPSCALER_PIPELINE_H
#define AI_UPSCALER_PIPELINE_H

#include <string>
#include <vector>
#include <net.h>
#include <gpu.h>
#include "LutColorEngine.h"

class AiUpscalerPipeline {
public:
    enum Mode {
        FAST_AI = 0,
        DETAIL = 1,
        ULTRA_DETAIL = 2,
        CULINARY_PRO = 3,
        NIGHT_PRO = 4
    };

    AiUpscalerPipeline();
    ~AiUpscalerPipeline();

    int init(const char* modelPath, int mode);
    int process(const float* inBayer, int width, int height, float* outRgb, int targetWidth, int targetHeight);

private:
    ncnn::Net net;
    ncnn::VulkanDevice* vkdev = nullptr;
    int currentMode = 0;
    koshcam::LutColorEngine lutEngine;

    void applyCulinaryProEnhancements(float* outRgb, int width, int height);
    void applyNightProEnhancements(float* outRgb, int width, int height);
};

#endif
