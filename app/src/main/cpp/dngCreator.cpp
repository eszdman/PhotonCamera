//
// Created by eszdman on 02.06.2025.
//
#include <jni.h>
#include <android/log.h>
#include <cstdlib>
#include <string>
#include <sstream>
#include <cmath>
#include <algorithm>
#include <vector>
#include <cstdint>
#include <ctime>
#include <cstdio>
#include <dlfcn.h>
#include <sys/types.h>
#define TINY_DNG_WRITER_IMPLEMENTATION
#include "deps/tiny_dng_writer.h"
#include "dngCreator.h"

// libarchive public headers — used for opaque types, constants, and function
// pointer signatures.  The actual symbols come from libarchive-jni.so which is
// loaded at runtime via dlopen (the library has no prefab / CMake export).
#include <archive.h>
#include <archive_entry.h>

static const double COMPRESSION_GAMMA = 2.2;
static const int STORED_LEVELS_10 = 1024;
static const int MAX_INPUT_LEVELS = 65536;
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG,"dng_creator_native", __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, "dng_creator_native", __VA_ARGS__)

// ---------------------------------------------------------------------------
// libarchive runtime loader
//
// libarchive-android (me.zhanghai.android.libarchive) bundles libarchive as a
// static library inside archive-jni.so.  That AAR does not publish a prefab
// package, so we cannot link against it at CMake time.  Instead we dlopen the
// shared library at runtime and resolve the symbols we need via dlsym.
// The archive.h / archive_entry.h headers above give us the opaque struct
// declarations, constants (AE_IFREG, ARCHIVE_OK …) and the precise function
// signatures for the function-pointer typedefs below.
// ---------------------------------------------------------------------------

struct LibArchive {
    void* handle   = nullptr;
    bool  loaded   = false;   // load() has been attempted
    bool  available = false;  // load() succeeded

    // --- write API ---
    struct archive*       (*write_new)             (void)                                                   = nullptr;
    int                   (*write_set_format_zip)  (struct archive*)                                        = nullptr;
    int                   (*write_set_format_option)(struct archive*, const char*, const char*, const char*) = nullptr;
    int                   (*write_add_filter_none) (struct archive*)                                        = nullptr;
    int                   (*write_open_fd)         (struct archive*, int)                                   = nullptr;
    int                   (*write_open_filename)   (struct archive*, const char*)                           = nullptr;
    int                   (*write_header)          (struct archive*, struct archive_entry*)                 = nullptr;
    la_ssize_t            (*write_data)            (struct archive*, const void*, size_t)                   = nullptr;
    int                   (*write_close)           (struct archive*)                                        = nullptr;
    int                   (*write_free)            (struct archive*)                                        = nullptr;

    // --- entry API ---
    struct archive_entry* (*entry_new)                    (void)                                                   = nullptr;
    struct archive_entry* (*entry_clear)                  (struct archive_entry*)                                  = nullptr;
    void                  (*entry_set_pathname)           (struct archive_entry*, const char*)                     = nullptr;
    void                  (*entry_set_size)               (struct archive_entry*, la_int64_t)                      = nullptr;
    void                  (*entry_set_filetype)           (struct archive_entry*, unsigned int)                    = nullptr;
    void                  (*entry_set_perm)               (struct archive_entry*, mode_t)                          = nullptr;
    void                  (*entry_set_mtime)              (struct archive_entry*, time_t, long)                    = nullptr;
    void                  (*entry_free)                   (struct archive_entry*)                                  = nullptr;

    bool load() {
        if (loaded) return available;
        loaded = true;

        handle = dlopen("libarchive-jni.so", RTLD_NOW);
        if (!handle) {
            LOGE("LibArchive dlopen failed: %s", dlerror());
            return false;
        }

#define LOAD_SYM(member, sym)                                                  \
        member = reinterpret_cast<decltype(member)>(dlsym(handle, sym));       \
        if (!member) LOGE("LibArchive dlsym '%s' failed", sym)

        LOAD_SYM(write_new,              "archive_write_new");
        LOAD_SYM(write_set_format_zip,   "archive_write_set_format_zip");
        LOAD_SYM(write_set_format_option,"archive_write_set_format_option");
        LOAD_SYM(write_add_filter_none,  "archive_write_add_filter_none");
        LOAD_SYM(write_open_fd,          "archive_write_open_fd");
        LOAD_SYM(write_open_filename,    "archive_write_open_filename");
        LOAD_SYM(write_header,           "archive_write_header");
        LOAD_SYM(write_data,             "archive_write_data");
        LOAD_SYM(write_close,            "archive_write_close");
        LOAD_SYM(write_free,             "archive_write_free");
        LOAD_SYM(entry_new,                     "archive_entry_new");
        LOAD_SYM(entry_clear,                   "archive_entry_clear");
        LOAD_SYM(entry_set_pathname,            "archive_entry_set_pathname");
        LOAD_SYM(entry_set_size,                "archive_entry_set_size");
        LOAD_SYM(entry_set_filetype,            "archive_entry_set_filetype");
        LOAD_SYM(entry_set_perm,                "archive_entry_set_perm");
        LOAD_SYM(entry_set_mtime,               "archive_entry_set_mtime");
        LOAD_SYM(entry_free,                    "archive_entry_free");
#undef LOAD_SYM

        available = (write_new != nullptr && write_header != nullptr && entry_new != nullptr);
        if (available)
            LOGD("LibArchive loaded successfully from libarchive-jni.so");
        else
            LOGE("LibArchive: one or more required symbols missing");
        return available;
    }
};

static LibArchive g_libarchive;

extern "C" {

class DngCreator {
    tinydngwriter::DNGImage *dng_image0 = nullptr;

    // libarchive write state (nullptr when no archive is open)
    struct archive*       archive_handle  = nullptr;
    struct archive_entry* archive_entry_h = nullptr;


    /** Build linearization + encoding tables when input white level > 10-bit and target bps is 10. Uses gamma curve. */
    void build_compression_tables() {
        const double whiteLevel = metadata.white_level;
        if (whiteLevel <= 1023.0 || metadata.bps != 10) return;
        const int inputLevels = static_cast<int>(whiteLevel) + 1;
        const int tableSize = std::min(inputLevels, MAX_INPUT_LEVELS);
        if (encoding_table) {
            delete[] encoding_table;
            encoding_table = nullptr;
        }
        encoding_table = new unsigned short[static_cast<size_t>(tableSize)];
        encoding_table_size = tableSize;
        /* Encoding: linear x in [0, whiteLevel] -> stored s in [0, 1023]: s = 1023 * (x/whiteLevel)^(1/gamma) */
        for (int i = 0; i < tableSize; i++) {
            double x = static_cast<double>(i) / whiteLevel;
            if (x <= 0.0) {
                encoding_table[i] = 0;
                continue;
            }
            double s = 1023.0 * std::pow(x, 1.0 / COMPRESSION_GAMMA);
            encoding_table[i] = static_cast<unsigned short>(std::min(1023.0, std::round(s)));
        }
        /* Linearization: stored s in [0, 1023] -> linear for DNG (0..65535). Inverse: linear = whiteLevel * (s/1023)^gamma; DNG value = linear*65535/whiteLevel */
        for (int s = 0; s < STORED_LEVELS_10; s++) {
            double t = static_cast<double>(s) / 1023.0;
            double linear = whiteLevel * std::pow(t, COMPRESSION_GAMMA);
            double dngLinear = (linear / whiteLevel) * 65535.0;
            linearization_table[s] = static_cast<unsigned short>(std::min(65535.0, std::round(dngLinear)));
        }
        LOGD("DNG compression tables: whiteLevel=%.0f gamma=%.2f encoding_table_size=%d", whiteLevel, COMPRESSION_GAMMA, encoding_table_size);
    }

public:
    /** When input white level > 10-bit and bps==10: linearization table for DNG tag (stored value -> linear 0..65535). */
    unsigned short linearization_table[STORED_LEVELS_10];
    /** Encoding table: linear input value -> 10-bit stored value. Allocated when needed, size up to MAX_INPUT_LEVELS. */
    unsigned short* encoding_table = nullptr;
    int encoding_table_size = 0;
    DngMetadata metadata;
    DngCreator() {
        LOGD("DngCreator initialized");
        dng_image0 = new tinydngwriter::DNGImage();
        dng_image0->SetSubfileType(false, false, false);
        dng_image0->SetBigEndian(false);
        dng_image0->SetSamplesPerPixel(1);
        dng_image0->SetPhotometric(tinydngwriter::PHOTOMETRIC_CFA);
        dng_image0->SetPlanarConfig(tinydngwriter::PLANARCONFIG_CONTIG);
        //dng_image0->SetCompression(tinydngwriter::COMPRESSION_NEW_JPEG);
        dng_image0->SetDNGVersion(1, 3, 0, 0);
        dng_image0->SetExifVersion("0220");
        metadata.bps = 16;
    }

    ~DngCreator() {
        if (archive_handle) closeArchive();
        if (dng_image0) {
            delete dng_image0;
        }
        if (encoding_table) {
            delete[] encoding_table;
            encoding_table = nullptr;
        }
    }

    // --- Archive API (backed by libarchive via dlopen) ---

    // Shared init: ZIP format, STORED entries (no compression — fastest possible).
    void setupArchiveFormat() {
        g_libarchive.write_set_format_zip(archive_handle);
        // No outer filter wrapper; ZIP handles per-entry compression internally.
        g_libarchive.write_add_filter_none(archive_handle);

        if (g_libarchive.write_set_format_option) {
            // Current: STORED — zero CPU cost, maximum write speed.
            g_libarchive.write_set_format_option(archive_handle, "zip", "compression", "store");

            // To switch to DEFLATE instead, replace the line above with:
            //   g_libarchive.write_set_format_option(archive_handle, "zip", "compression", "deflate");
            // Optionally also set the level (1 = fastest, 9 = best ratio):
            //   g_libarchive.write_set_format_option(archive_handle, "zip", "compression-level", "1");
        }
        LOGD("Archive: ZIP stored (no compression)");
    }

    void openArchive(const std::string& path) {
        if (archive_handle) closeArchive();
        if (!g_libarchive.load()) {
            LOGE("openArchive: libarchive not available");
            return;
        }
        archive_handle = g_libarchive.write_new();
        setupArchiveFormat();
        if (g_libarchive.write_open_filename(archive_handle, path.c_str()) != ARCHIVE_OK) {
            LOGE("archive_write_open_filename failed: %s", path.c_str());
            g_libarchive.write_free(archive_handle);
            archive_handle = nullptr;
            return;
        }
        archive_entry_h = g_libarchive.entry_new();
        LOGD("Archive opened (path): %s", path.c_str());
    }

    void openArchiveByFd(int fd) {
        if (archive_handle) closeArchive();
        if (!g_libarchive.load()) {
            LOGE("openArchiveByFd: libarchive not available");
            return;
        }
        archive_handle = g_libarchive.write_new();
        setupArchiveFormat();
        if (g_libarchive.write_open_fd(archive_handle, fd) != ARCHIVE_OK) {
            LOGE("archive_write_open_fd failed for fd=%d", fd);
            g_libarchive.write_free(archive_handle);
            archive_handle = nullptr;
            return;
        }
        archive_entry_h = g_libarchive.entry_new();
        LOGD("Archive opened (fd=%d)", fd);
    }

    void writeToArchive(const std::string& entryName, const void* data, size_t size) {
        if (!archive_handle || !archive_entry_h) return;

        g_libarchive.entry_clear(archive_entry_h);
        g_libarchive.entry_set_pathname(archive_entry_h, entryName.c_str());
        g_libarchive.entry_set_size(archive_entry_h, static_cast<la_int64_t>(size));
        g_libarchive.entry_set_filetype(archive_entry_h, AE_IFREG);
        g_libarchive.entry_set_perm(archive_entry_h, 0644);
        if (g_libarchive.entry_set_mtime)
            g_libarchive.entry_set_mtime(archive_entry_h, time(nullptr), 0);

        int r = g_libarchive.write_header(archive_handle, archive_entry_h);
        if (r != ARCHIVE_OK) {
            LOGE("archive_write_header failed (%d) for %s", r, entryName.c_str());
            return;
        }
        g_libarchive.write_data(archive_handle, data, size);
        LOGD("Archive entry: %s (%zu bytes)", entryName.c_str(), size);
    }

    void closeArchive() {
        if (!archive_handle) return;
        g_libarchive.write_close(archive_handle);
        g_libarchive.write_free(archive_handle);
        archive_handle = nullptr;
        if (archive_entry_h) {
            g_libarchive.entry_free(archive_entry_h);
            archive_entry_h = nullptr;
        }
        LOGD("Archive closed");
    }

    bool isArchiveOpen() const { return archive_handle != nullptr; }

    void setBitsPerSample(int bps) {
        metadata.bps = bps;
    }

    void setCompression(bool compression) {
        if (compression) {
            dng_image0->SetCompression(tinydngwriter::COMPRESSION_NEW_JPEG);
            metadata.compression = true;
        } else {
            dng_image0->SetCompression(tinydngwriter::COMPRESSION_NONE);
            metadata.compression = false;
        }
    }

    void setOrientation(int orientation) {
        metadata.orientation = orientation;
    }

    void setWhiteLevel(double whiteLevel) {
        metadata.white_level = whiteLevel;
    }

    void setBlackLevel(const unsigned short* blackLevel) {
        for (int i = 0; i < 4; i++) {
            metadata.black_level[i] = blackLevel[i];
        }
    }

    void setColorMatrix1(const double* matrix) {
        for (int i = 0; i < 9; i++) {
            metadata.color_matrix1[i] = matrix[i];
        }
        metadata.has_color_matrix1 = true;
    }

    void setColorMatrix2(const double* matrix) {
        for (int i = 0; i < 9; i++) {
            metadata.color_matrix2[i] = matrix[i];
        }
        metadata.has_color_matrix2 = true;
    }

    void setForwardMatrix1(const double* matrix) {
        for (int i = 0; i < 9; i++) {
            metadata.forward_matrix1[i] = matrix[i];
        }
        metadata.has_forward_matrix1 = true;
    }

    void setForwardMatrix2(const double* matrix) {
        for (int i = 0; i < 9; i++) {
            metadata.forward_matrix2[i] = matrix[i];
        }
        metadata.has_forward_matrix2 = true;
    }

    void setCameraCalibration1(const double* matrix) {
        for (int i = 0; i < 9; i++) {
            metadata.camera_calibration1[i] = matrix[i];
        }
        metadata.has_camera_calibration1 = true;
    }

    void setCameraCalibration2(const double* matrix) {
        for (int i = 0; i < 9; i++) {
            metadata.camera_calibration2[i] = matrix[i];
        }
        metadata.has_camera_calibration2 = true;
    }

    void setAsShotNeutral(const double* neutral) {
        for (int i = 0; i < 3; i++) {
            metadata.as_shot_neutral[i] = neutral[i];
        }
        metadata.has_as_shot_neutral = true;
    }

    void setAsShotWhiteXY(double x, double y) {
        metadata.as_shot_white_xy[0] = x;
        metadata.as_shot_white_xy[1] = y;
        metadata.has_as_shot_white_xy = true;
    }

    void setAnalogBalance(const double* balance) {
        for (int i = 0; i < 3; i++) {
            metadata.analog_balance[i] = balance[i];
        }
        metadata.has_analog_balance = true;
    }

    void setCalibrationIlluminant1(unsigned short illuminant) {
        metadata.calibration_illuminant1 = illuminant;
    }

    void setCalibrationIlluminant2(unsigned short illuminant) {
        metadata.calibration_illuminant2 = illuminant;
    }

    void setUniqueCameraModel(const std::string& model) {
        dng_image0->SetUniqueCameraModel(model);
    }

    void setDescription(std::string description){
        dng_image0->SetImageDescription(description);
    }

    void setSoftware(std::string software){
        dng_image0->SetSoftware(software);
    }

    void setIso(unsigned short iso) {
        dng_image0->SetIso(iso);
    }

    void setExposureTime(double exposureTime) {
        dng_image0->SetExposureTime(exposureTime);
    }

    void setAperture(double aperture) {
        dng_image0->SetAperture(aperture);
    }

    void setFocalLength(double focalLength) {
        dng_image0->SetFocalLength(focalLength);
    }

    void setMake(const std::string& make) {
        dng_image0->SetMake(make);
    }

    void setModel(const std::string& model) {
        dng_image0->SetModel(model);
    }

    void setTimeCode(const unsigned char* timecode) {
        unsigned char tc[8];
        for (int i = 0; i < 8; i++) {
            tc[i] = timecode[i];
        }
        dng_image0->SetTimeCode(tc);
    }

    void setDateTime(const std::string& datetime) {
        dng_image0->SetDateTime(datetime);
    }

    void setNoiseProfile(const double* noiseProfile, int numValues) {
        unsigned int numPlanes = numValues / 2;
        dng_image0->SetNoiseProfile(numPlanes, noiseProfile);
    }

    void setFrameRate(double frameRate) {
        metadata.frame_rate = frameRate;
        metadata.has_frame_rate = true;
        dng_image0->SetFrameRate(frameRate);
    }

    void setCFAPattern(int pattern) {
        // Define CFA patterns based on enum values
        metadata.cfa = pattern;
        switch (metadata.cfa) {
            case 0: // CFA_PATTERN_RGGB
                metadata.cfa_pattern[0] = 0; // Red
                metadata.cfa_pattern[1] = 1; // Green
                metadata.cfa_pattern[2] = 1; // Green
                metadata.cfa_pattern[3] = 2; // Blue
                break;
            case 1: // CFA_PATTERN_GRBG
                metadata.cfa_pattern[0] = 1; // Green
                metadata.cfa_pattern[1] = 0; // Red
                metadata.cfa_pattern[2] = 2; // Blue
                metadata.cfa_pattern[3] = 1; // Green
                break;
            case 2: // CFA_PATTERN_GBRG
                metadata.cfa_pattern[0] = 1; // Green
                metadata.cfa_pattern[1] = 2; // Blue
                metadata.cfa_pattern[2] = 0; // Red
                metadata.cfa_pattern[3] = 1; // Green
                break;
            case 3: // CFA_PATTERN_BGGR
                metadata.cfa_pattern[0] = 2; // Blue
                metadata.cfa_pattern[1] = 1; // Green
                metadata.cfa_pattern[2] = 1; // Green
                metadata.cfa_pattern[3] = 0; // Red
                break;
            default:
                // Default to RGGB
                metadata.cfa_pattern[0] = 0; // Red
                metadata.cfa_pattern[1] = 1; // Green
                metadata.cfa_pattern[2] = 1; // Green
                metadata.cfa_pattern[3] = 2; // Blue
                break;
        }
    }

    void setGainMap(const float* gainMap, int xmin, int ymin, int xmax, int ymax, int width, int height) {
        float* gainMap0 = new float[width * height];
        float* gainMap1 = new float[width * height];
        float* gainMap2 = new float[width * height];
        float* gainMap3 = new float[width * height];
        for (int x = 0; x < width*height; x++) {
                gainMap0[x] = gainMap[4*x];
                gainMap1[x] = gainMap[4*x + 1];
                gainMap2[x] = gainMap[4*x + 2];
                gainMap3[x] = gainMap[4*x + 3];
        }
        std::vector<tinydngwriter::GainMap> gainMaps;
        // Create gain maps for each channel
        gainMaps.push_back(tinydngwriter::GainMap(gainMap0, width, height, xmin, ymin, xmax, ymax));
        gainMaps.push_back(tinydngwriter::GainMap(gainMap1, width, height, xmin, ymin, xmax, ymax));
        gainMaps.push_back(tinydngwriter::GainMap(gainMap2, width, height, xmin, ymin, xmax, ymax));
        gainMaps.push_back(tinydngwriter::GainMap(gainMap3, width, height, xmin, ymin, xmax, ymax));
        switch (metadata.cfa) {
            case 0:
                gainMaps[0].top = 0;
                gainMaps[0].left = 0;
                gainMaps[1].top = 0;
                gainMaps[1].left = 1;
                gainMaps[2].top = 1;
                gainMaps[2].left = 0;
                gainMaps[3].top = 1;
                gainMaps[3].left = 1;
                break;
            case 1:
                gainMaps[0].top = 0;
                gainMaps[0].left = 1;
                gainMaps[1].top = 0;
                gainMaps[1].left = 0;
                gainMaps[2].top = 1;
                gainMaps[2].left = 1;
                gainMaps[3].top = 1;
                gainMaps[3].left = 0;
                break;
            case 2:
                gainMaps[0].top = 1;
                gainMaps[0].left = 0;
                gainMaps[1].top = 0;
                gainMaps[1].left = 0;
                gainMaps[2].top = 1;
                gainMaps[2].left = 1;
                gainMaps[3].top = 0;
                gainMaps[3].left = 1;
                break;
            case 3:
                gainMaps[0].top = 1;
                gainMaps[0].left = 1;
                gainMaps[1].top = 0;
                gainMaps[1].left = 1;
                gainMaps[2].top = 1;
                gainMaps[2].left = 0;
                gainMaps[3].top = 0;
                gainMaps[3].left = 0;
                break;
        }
        int activeX = (xmax - xmin) - 1;
        int activeY = (ymax - ymin) - 1;
        gainMaps[0].bottom = activeY;
        gainMaps[0].right = activeX;
        gainMaps[1].bottom = activeY;
        gainMaps[1].right = activeX;
        gainMaps[2].bottom = activeY;
        gainMaps[2].right = activeX;
        gainMaps[3].bottom = activeY;
        gainMaps[3].right = activeX;
        dng_image0->SetGainMap(gainMaps);
    }

    void setBinning(bool binning) {
        metadata.binning = binning;
    }

    /**
     * Bayer 2x2 binning: each output pixel combines 4 same-color input pixels
     * (from a 4x4 input block → 2x2 output Bayer cell), preserving the CFA pattern.
     *
     * useAverage=true  → average the 4 pixels (levels unchanged, for 16-bit full-range input).
     * useAverage=false → sum the 4 pixels  (white/black levels ×4, for sub-16-bit input).
     */
    uint16_t* applyBayerBinning(const void* inputData, int srcWidth, int srcHeight,
                                int& outWidth, int& outHeight, bool useAverage) {
        outWidth  = srcWidth  / 2;
        outHeight = srcHeight / 2;
        uint16_t* output = new uint16_t[static_cast<size_t>(outWidth) * static_cast<size_t>(outHeight)];
        const uint16_t* input = reinterpret_cast<const uint16_t*>(inputData);

        for (int oy = 0; oy < outHeight; oy++) {
            // Within the Bayer cell: row offset dr ∈ {0,1}; map to 4-row input block
            int blockStartRow = (oy / 2) * 4;
            int dr    = oy % 2;
            int inRow  = blockStartRow + dr;
            int inRow2 = std::min(inRow + 2, srcHeight - 1); // next same-color row

            for (int ox = 0; ox < outWidth; ox++) {
                int blockStartCol = (ox / 2) * 4;
                int dc    = ox % 2;
                int inCol  = blockStartCol + dc;
                int inCol2 = std::min(inCol + 2, srcWidth - 1); // next same-color col

                uint32_t sum =
                    (uint32_t)input[inRow  * srcWidth + inCol ] +
                    (uint32_t)input[inRow  * srcWidth + inCol2] +
                    (uint32_t)input[inRow2 * srcWidth + inCol ] +
                    (uint32_t)input[inRow2 * srcWidth + inCol2];

                output[oy * outWidth + ox] = useAverage
                    ? static_cast<uint16_t>(sum >> 2)
                    : static_cast<uint16_t>(std::min(sum, (uint32_t)65535u));
            }
        }
        LOGD("Bayer binning: %dx%d -> %dx%d (%s)", srcWidth, srcHeight, outWidth, outHeight,
             useAverage ? "average" : "sum");
        return output;
    }

    void* createDng(void* imageData, int width, int height, size_t &size) {
        metadata.original_width  = width;
        metadata.original_height = height;

        void*     dataToProcess = imageData;
        int       actualWidth   = width;
        int       actualHeight  = height;
        uint16_t* binnedData    = nullptr;

        if (metadata.binning) {
            bool useAverage = (metadata.white_level >= 65535.0);
            metadata.binning_uses_average = useAverage;
            binnedData    = applyBayerBinning(imageData, width, height,
                                              actualWidth, actualHeight, useAverage);
            dataToProcess = binnedData;
            if (!useAverage) {
                metadata.white_level = std::min(metadata.white_level * 4.0, 65535.0);
                for (int i = 0; i < 4; i++) {
                    uint32_t bl = static_cast<uint32_t>(metadata.black_level[i]) * 4;
                    metadata.black_level[i] = static_cast<unsigned short>(
                        std::min(bl, (uint32_t)65535u));
                }
            }
        }

        metadata.width  = actualWidth;
        metadata.height = actualHeight;
        tinydngwriter::DNGWriter dng_writer(false);
        dng_image0->SetBitsPerSample(1, &metadata.bps);
        dng_image0->SetOrientation(metadata.orientation);
        dng_image0->SetImageWidth(static_cast<unsigned int>(actualWidth));
        dng_image0->SetImageLength(static_cast<unsigned int>(actualHeight));
        dng_image0->SetRowsPerStrip(actualHeight);
        dng_image0->SetActiveArea(new unsigned int[4]{0, 0, static_cast<unsigned int>(actualHeight), static_cast<unsigned int>(actualWidth)});
        dng_image0->SetResolutionUnit(tinydngwriter::RESUNIT_CENTIMETER);
        dng_image0->SetXResolution(72.f);
        dng_image0->SetYResolution(72.f);

        // Set black level repeat dimensions
        dng_image0->SetBlackLevelRepeatDim(2, 2);

        // Set CFA pattern
        dng_image0->SetCFARepeatPatternDim(2, 2);
        dng_image0->SetCFAPattern(4, metadata.cfa_pattern);
    
        // When input white level is >10-bit and we output 10-bit, use gamma compression + linearization table
        if (metadata.bps == 10 && metadata.white_level > 1023.0) {
            build_compression_tables();
            if (encoding_table) {
                dng_image0->SetLinearizationTable(static_cast<unsigned int>(STORED_LEVELS_10), linearization_table);
                const double newLevel = 65535;
                dng_image0->SetWhiteLevelRational(1, &newLevel);
                // set Black level too
                unsigned short compressedBlackLevel[4];
                for(int i =0; i<4;i++)
                    compressedBlackLevel[i] = static_cast<unsigned short>((newLevel / metadata.white_level) * metadata.black_level[i]);
                dng_image0->SetBlackLevel(4, compressedBlackLevel);
            }
        } else {
            dng_image0->SetBlackLevel(4, metadata.black_level);
            dng_image0->SetWhiteLevelRational(1, &metadata.white_level);
        }

        // Set color matrices
        if (metadata.has_color_matrix1) {
            dng_image0->SetColorMatrix1(3, metadata.color_matrix1);
        }
        if (metadata.has_color_matrix2) {
            dng_image0->SetColorMatrix2(3, metadata.color_matrix2);
        }

        // Set forward matrices
        if (metadata.has_forward_matrix1) {
            dng_image0->SetForwardMatrix1(3, metadata.forward_matrix1);
        }
        if (metadata.has_forward_matrix2) {
            dng_image0->SetForwardMatrix2(3, metadata.forward_matrix2);
        }
        
        // Set camera calibration matrices
        if (metadata.has_camera_calibration1) {
            dng_image0->SetCameraCalibration1(3, metadata.camera_calibration1);
        }
        if (metadata.has_camera_calibration2) {
            dng_image0->SetCameraCalibration2(3, metadata.camera_calibration2);
        }

        // Set white balance info
        if (metadata.has_as_shot_neutral) {
            dng_image0->SetAsShotNeutral(3, metadata.as_shot_neutral);
        }
        if (metadata.has_as_shot_white_xy) {
            dng_image0->SetAsShotWhiteXY(metadata.as_shot_white_xy[0], metadata.as_shot_white_xy[1]);
        }
        if (metadata.has_analog_balance) {
            dng_image0->SetAnalogBalance(3, metadata.analog_balance);
        }

        // Set illuminants
        dng_image0->SetCalibrationIlluminant1(metadata.calibration_illuminant1);
        dng_image0->SetCalibrationIlluminant2(metadata.calibration_illuminant2);

        if(metadata.compression && metadata.bps == 16) {
            dng_image0->SetImageDataJpeg(
                    reinterpret_cast<const unsigned short *>(dataToProcess),
                    static_cast<unsigned int>(actualWidth),
                    static_cast<unsigned int>(actualHeight),
                    metadata.bps);
        } else {
            // When encoding tables exist (10-bit output from >10-bit input), apply encoding + packing in-place
            if (metadata.bps == 10) {
                const size_t num_samples = static_cast<size_t>(actualWidth) * static_cast<size_t>(actualHeight);
                const size_t packed_size = (num_samples * 10 + 7) / 8;  // Round up to bytes
                LOGD("Applying in-place encoding+packing: %zu samples -> %zu bytes (10-bit)", num_samples, packed_size);
                // In-place: read as uint16_t*, write as uint8_t* (safe because packed is smaller)
                pack_10bit_big_endian_fast(
                        reinterpret_cast<const uint16_t*>(dataToProcess),
                        reinterpret_cast<uint8_t*>(dataToProcess),
                        num_samples,
                        encoding_table,
                        encoding_table_size);
                dng_image0->SetImageData(reinterpret_cast<const unsigned char*>(dataToProcess), packed_size);
            } else {
                dng_image0->SetImageData(reinterpret_cast<const unsigned char*>(dataToProcess),
                                         actualWidth * actualHeight * metadata.bps / 8);
            }
        }
        dng_writer.AddImage(dng_image0);

        size_t writeSize = 0;
        std::string err;
        auto res = dng_writer.WriteToMemory(&writeSize, &err, &metadata.strip_offset);
        if (!err.empty()) {
            LOGE("WriteToMemory error: %s", err.c_str());
        }
        if (binnedData) {
            delete[] binnedData;
        }
        size = writeSize;
        return res;
    }
    /** Packs 10-bit samples to big-endian bytes. If encoding_table is non-null, input values are mapped through it first (for >10-bit linear to 10-bit gamma-compressed). */
    void pack_10bit_big_endian_fast(const uint16_t* input, uint8_t* output, size_t num_samples,
                                    const unsigned short* encoding_table = nullptr, int encoding_table_size = 0) {
        auto to_10bit = [encoding_table, encoding_table_size](const uint16_t* inp, size_t idx) -> uint16_t {
            if (encoding_table && encoding_table_size > 0) {
                unsigned int v = inp[idx];
                if (v >= static_cast<unsigned int>(encoding_table_size)) return 1023;
                return encoding_table[v] & 0x3FF;
            }
            return inp[idx] & 0x3FF;
        };
        uint8_t* out = output;
        size_t i = 0;

        // Process blocks of 4 samples -> 5 bytes
        for (; i + 3 < num_samples; i += 4) {
            uint16_t a = to_10bit(input, i);
            uint16_t b = to_10bit(input, i + 1);
            uint16_t c = to_10bit(input, i + 2);
            uint16_t d = to_10bit(input, i + 3);

            // Pack into 5 bytes (big‑endian bit order)
            out[0] = (a >> 2) & 0xFF;                    // bits 9..2 of a
            out[1] = ((a & 0x3) << 6) | ((b >> 4) & 0x3F); // lower 2 bits of a + top 6 bits of b
            out[2] = ((b & 0xF) << 4) | ((c >> 6) & 0xF);  // lower 4 bits of b + top 4 bits of c
            out[3] = ((c & 0x3F) << 2) | ((d >> 8) & 0x3); // lower 6 bits of c + top 2 bits of d
            out[4] = d & 0xFF;                             // lower 8 bits of d

            out += 5;
        }

        // Handle remaining samples (0..3) using a fast 64‑bit accumulator
        if (i < num_samples) {
            uint64_t accumulator = 0;
            int bits_in_acc = 0;

            while (i < num_samples) {
                uint16_t value = to_10bit(input, i);
                // Append 10 bits to the accumulator (big‑endian style)
                accumulator = (accumulator << 10) | value;
                bits_in_acc += 10;

                // While we have at least 8 bits in the accumulator, output a byte
                while (bits_in_acc >= 8) {
                    bits_in_acc -= 8;
                    *out++ = (accumulator >> bits_in_acc) & 0xFF;
                }
                i++;
            }
            // If there are leftover bits (<8), they remain in the accumulator.
            // The DNG spec requires that the final byte be padded with zeros in the LSBs.
            // Our accumulator already has the bits left‑justified, so we must shift right
            // to align the leftover bits to the MSB of the next byte.
            if (bits_in_acc > 0) {
                // The leftover bits are the highest bits of accumulator.
                // We need to output them as the MSBs of the final byte.
                // Since we've been shifting out full bytes from the accumulator,
                // the remaining bits are already in the correct position (aligned to the left).
                *out = (accumulator << (8 - bits_in_acc)) & 0xFF;
                // Note: no need to increment out; we're done.
            }
        }
    }
};

    // JNI Functions

    JNIEXPORT jlong JNICALL Java_com_particlesdevs_photoncamera_processing_DngCreator_create(JNIEnv *env, jobject obj) {
        DngCreator* creator = new DngCreator();
        LOGD("DngCreator created at %p", creator);
        return reinterpret_cast<jlong>(creator);
    }

    JNIEXPORT jobject JNICALL Java_com_particlesdevs_photoncamera_processing_DngCreator_createDNG(JNIEnv *env, jobject obj, jlong creatorPtr, jint width, jint height, jobject imageData) {
        DngCreator* creator = reinterpret_cast<DngCreator*>(creatorPtr);
        if (!creator) {
            LOGE("DngCreator is null");
            return nullptr;
        }
        void* data = env->GetDirectBufferAddress(imageData);
        size_t size = 0;


        void* dngData = creator->createDng(data, width, height, size);
        if (!dngData) {
            return nullptr;
        }
        
        // Create ByteBuffer to return
        jobject byteBuffer = env->NewDirectByteBuffer(dngData, size);
        return byteBuffer;
    }

    JNIEXPORT void JNICALL Java_com_particlesdevs_photoncamera_processing_DngCreator_setOrientation(
        JNIEnv *env, jobject obj, jlong creatorPtr, jint orientation) {
        DngCreator* creator = reinterpret_cast<DngCreator*>(creatorPtr);
        if (creator) {
            creator->setOrientation(orientation);
        }
    }

    JNIEXPORT void JNICALL Java_com_particlesdevs_photoncamera_processing_DngCreator_setWhiteLevel(JNIEnv *env, jobject obj, jlong creatorPtr, jdouble whiteLevel) {
        DngCreator* creator = reinterpret_cast<DngCreator*>(creatorPtr);
        if (creator) {
            creator->setWhiteLevel(whiteLevel);
        }
    }

    JNIEXPORT void JNICALL Java_com_particlesdevs_photoncamera_processing_DngCreator_setBlackLevel(JNIEnv *env, jobject obj, jlong creatorPtr, jshortArray blackLevel) {
        DngCreator* creator = reinterpret_cast<DngCreator*>(creatorPtr);
        if (creator && blackLevel) {
            jshort* levels = env->GetShortArrayElements(blackLevel, nullptr);
            if (levels) {
                unsigned short bl[4];
                for (int i = 0; i < 4; i++) {
                    bl[i] = static_cast<unsigned short>(levels[i]);
                }
                creator->setBlackLevel(bl);
                env->ReleaseShortArrayElements(blackLevel, levels, 0);
            }
        }
    }

    JNIEXPORT void JNICALL Java_com_particlesdevs_photoncamera_processing_DngCreator_setColorMatrix1(JNIEnv *env, jobject obj, jlong creatorPtr, jdoubleArray matrix) {
        DngCreator* creator = reinterpret_cast<DngCreator*>(creatorPtr);
        if (creator && matrix) {
            jdouble* values = env->GetDoubleArrayElements(matrix, nullptr);
            if (values) {
                creator->setColorMatrix1(values);
                env->ReleaseDoubleArrayElements(matrix, values, 0);
            }
        }
    }

    JNIEXPORT void JNICALL Java_com_particlesdevs_photoncamera_processing_DngCreator_setColorMatrix2(JNIEnv *env, jobject obj, jlong creatorPtr, jdoubleArray matrix) {
        DngCreator* creator = reinterpret_cast<DngCreator*>(creatorPtr);
        if (creator && matrix) {
            jdouble* values = env->GetDoubleArrayElements(matrix, nullptr);
            if (values) {
                creator->setColorMatrix2(values);
                env->ReleaseDoubleArrayElements(matrix, values, 0);
            }
        }
    }

    JNIEXPORT void JNICALL Java_com_particlesdevs_photoncamera_processing_DngCreator_setForwardMatrix1(JNIEnv *env, jobject obj, jlong creatorPtr, jdoubleArray matrix) {
        DngCreator* creator = reinterpret_cast<DngCreator*>(creatorPtr);
        if (creator && matrix) {
            jdouble* values = env->GetDoubleArrayElements(matrix, nullptr);
            if (values) {
                creator->setForwardMatrix1(values);
                env->ReleaseDoubleArrayElements(matrix, values, 0);
            }
        }
    }

    JNIEXPORT void JNICALL Java_com_particlesdevs_photoncamera_processing_DngCreator_setForwardMatrix2(JNIEnv *env, jobject obj, jlong creatorPtr, jdoubleArray matrix) {
        DngCreator* creator = reinterpret_cast<DngCreator*>(creatorPtr);
        if (creator && matrix) {
            jdouble* values = env->GetDoubleArrayElements(matrix, nullptr);
            if (values) {
                creator->setForwardMatrix2(values);
                env->ReleaseDoubleArrayElements(matrix, values, 0);
            }
        }
    }

    JNIEXPORT void JNICALL Java_com_particlesdevs_photoncamera_processing_DngCreator_setCameraCalibration1(JNIEnv *env, jobject obj, jlong creatorPtr, jdoubleArray matrix) {
        DngCreator* creator = reinterpret_cast<DngCreator*>(creatorPtr);
        if (creator && matrix) {
            jdouble* values = env->GetDoubleArrayElements(matrix, nullptr);
            if (values) {
                creator->setCameraCalibration1(values);
                env->ReleaseDoubleArrayElements(matrix, values, 0);
            }
        }
    }

    JNIEXPORT void JNICALL Java_com_particlesdevs_photoncamera_processing_DngCreator_setCameraCalibration2(JNIEnv *env, jobject obj, jlong creatorPtr, jdoubleArray matrix) {
        DngCreator* creator = reinterpret_cast<DngCreator*>(creatorPtr);
        if (creator && matrix) {
            jdouble* values = env->GetDoubleArrayElements(matrix, nullptr);
            if (values) {
                creator->setCameraCalibration2(values);
                env->ReleaseDoubleArrayElements(matrix, values, 0);
            }
        }
    }

    JNIEXPORT void JNICALL Java_com_particlesdevs_photoncamera_processing_DngCreator_setAsShotNeutral(JNIEnv *env, jobject obj, jlong creatorPtr, jdoubleArray neutral) {
        DngCreator* creator = reinterpret_cast<DngCreator*>(creatorPtr);
        if (creator && neutral) {
            jdouble* values = env->GetDoubleArrayElements(neutral, nullptr);
            if (values) {
                creator->setAsShotNeutral(values);
                env->ReleaseDoubleArrayElements(neutral, values, 0);
            }
        }
    }

    JNIEXPORT void JNICALL Java_com_particlesdevs_photoncamera_processing_DngCreator_setAsShotWhiteXY(JNIEnv *env, jobject obj, jlong creatorPtr, jdouble x, jdouble y) {
        DngCreator* creator = reinterpret_cast<DngCreator*>(creatorPtr);
        if (creator) {
            creator->setAsShotWhiteXY(x, y);
        }
    }

    JNIEXPORT void JNICALL Java_com_particlesdevs_photoncamera_processing_DngCreator_setAnalogBalance(JNIEnv *env, jobject obj, jlong creatorPtr, jdoubleArray balance) {
        DngCreator* creator = reinterpret_cast<DngCreator*>(creatorPtr);
        if (creator && balance) {
            jdouble* values = env->GetDoubleArrayElements(balance, nullptr);
            if (values) {
                creator->setAnalogBalance(values);
                env->ReleaseDoubleArrayElements(balance, values, 0);
            }
        }
    }

    JNIEXPORT void JNICALL Java_com_particlesdevs_photoncamera_processing_DngCreator_setCalibrationIlluminant1(JNIEnv *env, jobject obj, jlong creatorPtr, jshort illuminant) {
        DngCreator* creator = reinterpret_cast<DngCreator*>(creatorPtr);
        if (creator) {
            creator->setCalibrationIlluminant1(static_cast<unsigned short>(illuminant));
        }
    }

    JNIEXPORT void JNICALL Java_com_particlesdevs_photoncamera_processing_DngCreator_setCalibrationIlluminant2(JNIEnv *env, jobject obj, jlong creatorPtr, jshort illuminant) {
        DngCreator* creator = reinterpret_cast<DngCreator*>(creatorPtr);
        if (creator) {
            creator->setCalibrationIlluminant2(static_cast<unsigned short>(illuminant));
        }
    }

    JNIEXPORT void JNICALL Java_com_particlesdevs_photoncamera_processing_DngCreator_setUniqueCameraModel(JNIEnv *env, jobject obj, jlong creatorPtr, jstring model) {
        DngCreator* creator = reinterpret_cast<DngCreator*>(creatorPtr);
        if (creator && model) {
            const char* modelStr = env->GetStringUTFChars(model, nullptr);
            if (modelStr) {
                creator->setUniqueCameraModel(std::string(modelStr));
                env->ReleaseStringUTFChars(model, modelStr);
            }
        }
    }

    JNIEXPORT void JNICALL Java_com_particlesdevs_photoncamera_processing_DngCreator_setDescription(JNIEnv *env, jobject obj, jlong creatorPtr, jstring model) {
        DngCreator* creator = reinterpret_cast<DngCreator*>(creatorPtr);
        if (creator && model) {
            const char* desc = env->GetStringUTFChars(model, nullptr);
            if (desc) {
                creator->setDescription(std::string(desc));
                env->ReleaseStringUTFChars(model, desc);
            }
        }
    }

    JNIEXPORT void JNICALL Java_com_particlesdevs_photoncamera_processing_DngCreator_setSoftware(JNIEnv *env, jobject obj, jlong creatorPtr, jstring model) {
        DngCreator* creator = reinterpret_cast<DngCreator*>(creatorPtr);
        if (creator && model) {
            const char* soft = env->GetStringUTFChars(model, nullptr);
            if (soft) {
                creator->setSoftware(std::string(soft));
                env->ReleaseStringUTFChars(model, soft);
            }
        }
    }

    JNIEXPORT void JNICALL Java_com_particlesdevs_photoncamera_processing_DngCreator_setIso(JNIEnv *env, jobject obj, jlong creatorPtr, jshort iso) {
        DngCreator* creator = reinterpret_cast<DngCreator*>(creatorPtr);
        if (creator) {
            creator->setIso(static_cast<unsigned short>(iso));
        }
    }

    JNIEXPORT void JNICALL Java_com_particlesdevs_photoncamera_processing_DngCreator_setExposureTime(JNIEnv *env, jobject obj, jlong creatorPtr, jdouble exposureTime) {
        DngCreator* creator = reinterpret_cast<DngCreator*>(creatorPtr);
        if (creator) {
            creator->setExposureTime(exposureTime);
        }
    }

    JNIEXPORT void JNICALL Java_com_particlesdevs_photoncamera_processing_DngCreator_setAperture(JNIEnv *env, jobject obj, jlong creatorPtr, jdouble aperture) {
        DngCreator* creator = reinterpret_cast<DngCreator*>(creatorPtr);
        if (creator) {
            creator->setAperture(aperture);
        }
    }

    JNIEXPORT void JNICALL Java_com_particlesdevs_photoncamera_processing_DngCreator_setFocalLength(JNIEnv *env, jobject obj, jlong creatorPtr, jdouble focalLength) {
        DngCreator* creator = reinterpret_cast<DngCreator*>(creatorPtr);
        if (creator) {
            creator->setFocalLength(focalLength);
        }
    }

    JNIEXPORT void JNICALL Java_com_particlesdevs_photoncamera_processing_DngCreator_setMake(JNIEnv *env, jobject obj, jlong creatorPtr, jstring make) {
        DngCreator* creator = reinterpret_cast<DngCreator*>(creatorPtr);
        if (creator && make) {
            const char* makeStr = env->GetStringUTFChars(make, nullptr);
            if (makeStr) {
                creator->setMake(std::string(makeStr));
                env->ReleaseStringUTFChars(make, makeStr);
            }
        }
    }

    JNIEXPORT void JNICALL Java_com_particlesdevs_photoncamera_processing_DngCreator_setModel(JNIEnv *env, jobject obj, jlong creatorPtr, jstring model) {
        DngCreator* creator = reinterpret_cast<DngCreator*>(creatorPtr);
        if (creator && model) {
            const char* modelStr = env->GetStringUTFChars(model, nullptr);
            if (modelStr) {
                creator->setModel(std::string(modelStr));
                env->ReleaseStringUTFChars(model, modelStr);
            }
        }
    }

    JNIEXPORT void JNICALL Java_com_particlesdevs_photoncamera_processing_DngCreator_setTimeCode(JNIEnv *env, jobject obj, jlong creatorPtr, jbyteArray timecode) {
        DngCreator* creator = reinterpret_cast<DngCreator*>(creatorPtr);
        if (creator && timecode) {
            jbyte* tc = env->GetByteArrayElements(timecode, nullptr);
            if (tc) {
                creator->setTimeCode(reinterpret_cast<const unsigned char*>(tc));
                env->ReleaseByteArrayElements(timecode, tc, 0);
            }
        }
    }

    JNIEXPORT void JNICALL Java_com_particlesdevs_photoncamera_processing_DngCreator_setDateTime(JNIEnv *env, jobject obj, jlong creatorPtr, jstring datetime) {
        DngCreator* creator = reinterpret_cast<DngCreator*>(creatorPtr);
        if (creator && datetime) {
            const char* dt = env->GetStringUTFChars(datetime, nullptr);
            if (dt) {
                creator->setDateTime(std::string(dt));
                env->ReleaseStringUTFChars(datetime, dt);
            }
        }
    }

    JNIEXPORT void JNICALL Java_com_particlesdevs_photoncamera_processing_DngCreator_setNoiseProfile(JNIEnv *env, jobject obj, jlong creatorPtr, jdoubleArray noiseProfile) {
        DngCreator* creator = reinterpret_cast<DngCreator*>(creatorPtr);
        if (creator && noiseProfile) {
            jsize arrayLength = env->GetArrayLength(noiseProfile);
            jdouble* values = env->GetDoubleArrayElements(noiseProfile, nullptr);
            if (values) {
                creator->setNoiseProfile(values, static_cast<int>(arrayLength));
                env->ReleaseDoubleArrayElements(noiseProfile, values, 0);
            }
        }
    }

    JNIEXPORT void JNICALL Java_com_particlesdevs_photoncamera_processing_DngCreator_setFrameRate(JNIEnv *env, jobject obj, jlong creatorPtr, jdouble frameRate) {
        DngCreator* creator = reinterpret_cast<DngCreator*>(creatorPtr);
        if (creator) {
            creator->setFrameRate(frameRate);
        }
    }

    JNIEXPORT void JNICALL Java_com_particlesdevs_photoncamera_processing_DngCreator_setCFAPattern(JNIEnv *env, jobject obj, jlong creatorPtr, jint pattern) {
        DngCreator* creator = reinterpret_cast<DngCreator*>(creatorPtr);
        if (creator) {
            creator->setCFAPattern(pattern);
        }
    }

    JNIEXPORT void JNICALL Java_com_particlesdevs_photoncamera_processing_DngCreator_setGainMap(JNIEnv *env, jobject obj, jlong creatorPtr, jfloatArray gainMap, jint xmin, jint ymin, jint xmax, jint ymax, jint width, jint height) {
        DngCreator* creator = reinterpret_cast<DngCreator*>(creatorPtr);
        if (creator && gainMap) {
            jfloat* map = env->GetFloatArrayElements(gainMap, nullptr);
            if (map) {
                creator->setGainMap(map, xmin, ymin, xmax, ymax, width, height);
                env->ReleaseFloatArrayElements(gainMap, map, 0);
            }
        }
    }

    JNIEXPORT void JNICALL Java_com_particlesdevs_photoncamera_processing_DngCreator_setCompression(JNIEnv *env, jobject obj, jlong creatorPtr, jboolean useCompression) {
        DngCreator* creator = reinterpret_cast<DngCreator*>(creatorPtr);
        if (creator) {
            creator->setCompression(useCompression);
        }
    }

    JNIEXPORT void JNICALL Java_com_particlesdevs_photoncamera_processing_DngCreator_setBinning(JNIEnv *env, jobject obj, jlong creatorPtr, jboolean binning) {
        DngCreator* creator = reinterpret_cast<DngCreator*>(creatorPtr);
        if (creator) {
            creator->setBinning(binning);
        }
    }

JNIEXPORT void JNICALL Java_com_particlesdevs_photoncamera_processing_DngCreator_setBitsPerSample(JNIEnv *env, jobject obj, jlong creatorPtr, jint bps) {
    DngCreator* creator = reinterpret_cast<DngCreator*>(creatorPtr);
    if (creator) {
        creator->setBitsPerSample(bps);
    }
}

    JNIEXPORT void JNICALL Java_com_particlesdevs_photoncamera_processing_DngCreator_destroy(JNIEnv *env, jobject obj, jlong creatorPtr) {
    DngCreator* creator = reinterpret_cast<DngCreator*>(creatorPtr);
    if (creator) {
        LOGD("DngCreator destroyed at %p", creator);
        delete creator;
    }
}

    JNIEXPORT void JNICALL Java_com_particlesdevs_photoncamera_processing_DngCreator_writeFile(JNIEnv *env, jobject obj, jlong creatorPtr, jobject dngData, jobject rawData, jstring path, jint offset) {
        DngCreator *creator = reinterpret_cast<DngCreator *>(creatorPtr);
        if (creator) {
            void *data = env->GetDirectBufferAddress(dngData);
            size_t size = env->GetDirectBufferCapacity(dngData);
            void *raw = env->GetDirectBufferAddress(rawData);
            uint8_t* input = static_cast<uint8_t*>(raw) + offset;

            uint16_t* binnedInput = nullptr;
            const uint16_t* packInput = reinterpret_cast<const uint16_t*>(input);
            size_t numSamples = static_cast<size_t>(creator->metadata.width) *
                                static_cast<size_t>(creator->metadata.height);

            if (creator->metadata.binning && creator->metadata.original_width > 0) {
                int binnedW, binnedH;
                binnedInput = creator->applyBayerBinning(
                        input,
                        creator->metadata.original_width,
                        creator->metadata.original_height,
                        binnedW, binnedH,
                        creator->metadata.binning_uses_average);
                packInput  = binnedInput;
                numSamples = static_cast<size_t>(binnedW) * static_cast<size_t>(binnedH);
            }

            creator->pack_10bit_big_endian_fast(
                    packInput,
                    reinterpret_cast<uint8_t*>(data) + creator->metadata.strip_offset + 8,
                    numSamples,
                    creator->encoding_table,
                    creator->encoding_table_size);

            if (binnedInput) {
                delete[] binnedInput;
            }

            const char *pathStr = env->GetStringUTFChars(path, nullptr);
            if (pathStr) {
                if (creator->isArchiveOpen()) {
                    // Derive just the filename component for the archive entry name
                    std::string fullPath(pathStr);
                    size_t sep = fullPath.find_last_of("/\\");
                    std::string entryName = (sep != std::string::npos)
                        ? fullPath.substr(sep + 1)
                        : fullPath;
                    creator->writeToArchive(entryName, data, size);
                } else {
                    FILE *file = fopen(pathStr, "wb");
                    if (file) {
                        fwrite(data, 1, size, file);
                        fclose(file);
                        LOGD("DNG file written to %s", pathStr);
                    } else {
                        LOGE("Failed to open file %s for writing", pathStr);
                    }
                }
                env->ReleaseStringUTFChars(path, pathStr);
            }
        }
    }

    JNIEXPORT void JNICALL Java_com_particlesdevs_photoncamera_processing_DngCreator_openArchive(JNIEnv *env, jobject obj, jlong creatorPtr, jstring path) {
        DngCreator *creator = reinterpret_cast<DngCreator *>(creatorPtr);
        if (creator && path) {
            const char *pathStr = env->GetStringUTFChars(path, nullptr);
            if (pathStr) {
                creator->openArchive(std::string(pathStr));
                env->ReleaseStringUTFChars(path, pathStr);
            }
        }
    }

    JNIEXPORT void JNICALL Java_com_particlesdevs_photoncamera_processing_DngCreator_openArchiveByFd(JNIEnv *env, jobject obj, jlong creatorPtr, jint fd) {
        DngCreator *creator = reinterpret_cast<DngCreator *>(creatorPtr);
        if (creator) {
            creator->openArchiveByFd(static_cast<int>(fd));
        }
    }

    JNIEXPORT void JNICALL Java_com_particlesdevs_photoncamera_processing_DngCreator_closeArchive(JNIEnv *env, jobject obj, jlong creatorPtr) {
        DngCreator *creator = reinterpret_cast<DngCreator *>(creatorPtr);
        if (creator) {
            creator->closeArchive();
        }
    }

    // Frees a buffer returned by createDNG (malloc'd by WriteToMemory).
    // NewDirectByteBuffer does not take ownership: without this the ~128 MB+
    // per DNG leaks permanently (GC cannot free malloc'd memory).
    JNIEXPORT void JNICALL Java_com_particlesdevs_photoncamera_processing_DngCreator_freeDNG(JNIEnv *env, jobject obj, jobject dngData) {
        if (dngData == nullptr) {
            return;
        }
        void* ptr = env->GetDirectBufferAddress(dngData);
        if (ptr == nullptr) {
            return;
        }
        free(ptr);
    }
}
