#ifndef JPEG_ENCODER_H
#define JPEG_ENCODER_H

#include <cstdint>
#include <string>

/**
 * High-performance JPEG encoder using libjpeg-turbo.
 * Optimized for 200MP massive buffers.
 */
class JpegEncoder {
public:
    struct Config {
        int width;
        int height;
        int quality = 98;
        bool accurateDCT = true;
        int subsampling = 1; // 1 = TJSAMP_444
    };

    static int encodeToDisk(const uint8_t* rgbBuffer, const std::string& path, const Config& cfg);

    // Encodes to a pre-opened File Descriptor (for SAF support)
    static int encodeToFd(const uint8_t* rgbBuffer, int fd, const Config& cfg);
};

#endif
