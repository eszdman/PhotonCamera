#include <turbojpeg.h>
#include <android/log.h>
#include <cstdio>
#include <cstdint>

#define LOG_TAG "JpegEncoder"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

class JpegEncoder {
public:
    bool encodeToFile(const uint8_t* rgbBuffer, int width, int height, int quality, const char* filepath) {
        tjhandle tjInstance = tjInitCompress();
        if (tjInstance == nullptr) {
            LOGE("Failed to init TurboJPEG");
            return false;
        }

        int outSubsamp = TJSAMP_420;
        int flags = 0;

        // Установка Accurate Integer Forward DCT (JDCT_ISLOW) при качестве >= 98
        // для предотвращения падения производительности SIMD кодека
        if (quality >= 98) {
            flags |= TJFLAG_ACCURATEDCT;
        } else {
            flags |= TJFLAG_FASTDCT;
        }

        unsigned char* jpegBuf = nullptr;
        unsigned long jpegSize = 0;

        int tjStatus = tjCompress2(
                tjInstance,
                rgbBuffer,
                width,
                0,
                height,
                TJPF_RGB,
                &jpegBuf,
                &jpegSize,
                outSubsamp,
                quality,
                flags
        );

        if (tjStatus != 0) {
            LOGE("TurboJPEG error: %s", tjGetErrorStr2(tjInstance));
            tjDestroy(tjInstance);
            if (jpegBuf) tjFree(jpegBuf);
            return false;
        }

        FILE* file = fopen(filepath, "wb");
        if (!file) {
            LOGE("Failed to open file: %s", filepath);
            tjDestroy(tjInstance);
            tjFree(jpegBuf);
            return false;
        }

        fwrite(jpegBuf, 1, jpegSize, file);
        fclose(file);

        tjDestroy(tjInstance);
        tjFree(jpegBuf);

        return true;
    }
};