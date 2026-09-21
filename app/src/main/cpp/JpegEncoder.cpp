#include "JpegEncoder.h"
#include <turbojpeg.h>
#include <cstdio>
#include <android/log.h>
#include <vector>
#include <unistd.h>

#define LOG_TAG "JpegEncoder"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

int JpegEncoder::encodeToDisk(const uint8_t* rgbBuffer, const std::string& path, const Config& cfg) {
    tjhandle compressor = tjInitCompress();
    if (!compressor) return -1;

    unsigned char* jpegBuf = nullptr;
    unsigned long jpegSize = 0;

    int flags = 0;
    if (cfg.accurateDCT) flags |= TJFLAG_ACCURATEDCT;

    int r = tjCompress2(compressor, rgbBuffer, cfg.width, 0, cfg.height, TJPF_RGB,
                       &jpegBuf, &jpegSize, TJSAMP_444, cfg.quality, flags);

    if (r == 0) {
        FILE* file = fopen(path.c_str(), "wb");
        if (file) {
            fwrite(jpegBuf, 1, jpegSize, file);
            fclose(file);
        } else {
            LOGE("Failed to open path: %s", path.c_str());
            r = -1;
        }
    } else {
        LOGE("tjCompress2 failed: %s", tjGetErrorStr2(compressor));
    }

    tjFree(jpegBuf);
    tjDestroy(compressor);
    return r;
}

int JpegEncoder::encodeToFd(const uint8_t* rgbBuffer, int fd, const Config& cfg) {
    tjhandle compressor = tjInitCompress();
    if (!compressor) return -1;

    unsigned char* jpegBuf = nullptr;
    unsigned long jpegSize = 0;

    int flags = 0;
    if (cfg.accurateDCT) flags |= TJFLAG_ACCURATEDCT;

    // Mali-G57 optimization: Use accurate DCT and no subsampling (4:4:4)
    int r = tjCompress2(compressor, rgbBuffer, cfg.width, 0, cfg.height, TJPF_RGB,
                       &jpegBuf, &jpegSize, TJSAMP_444, cfg.quality, flags);

    if (r == 0) {
        ssize_t written = write(fd, jpegBuf, jpegSize);
        if (written != (ssize_t)jpegSize) {
            LOGE("Short write to FD %d: %zd of %lu", fd, written, jpegSize);
            r = -1;
        }
    } else {
        LOGE("tjCompress2 failed: %s", tjGetErrorStr2(compressor));
    }

    tjFree(jpegBuf);
    tjDestroy(compressor);
    return r;
}
