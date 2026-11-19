#include "RawInput.h"
#include <craw/Writer.h>
#include <fcntl.h>
#include <unistd.h>
#include <fstream>
#include <iostream>
#include <filesystem>
#include <stdexcept>

void check(bool result, const char* message) {
    if (!result) throw std::runtime_error(message);
}
int main(int argc, char** argv) {
    if (argc != 2) return 2;
    std::filesystem::create_directories(argv[1]);
    for (int codec : {1, 3}) for (int cfa = 0; cfa < 4; ++cfa)
        for (bool raw10 : {false, true}) for (bool bin : {false, true}) {
        constexpr int width = 20, height = 16, top = 2, rows = 12;
        int rowBytes = raw10 ? width / 4 * 5 : width * 2, stride = rowBytes + 8;
        // Padding must not become pixels. The last row omits its padding.
        std::vector<uint8_t> raw((height - 1) * stride + rowBytes, 0xee);
        auto sample = [=](int x, int y) -> uint16_t {
            return ((x * 7919 + y * 3571) ^ 0xabcd) & (raw10 ? 1023 : 65535);
        };
        for (int y = 0; y < height; ++y) for (int x = 0; x < width; x += 4) {
            uint8_t* p = raw.data() + y * stride;
            if (raw10) {
                p[x / 4 * 5 + 4] = 0;
                for (int i = 0; i < 4; ++i) {
                    uint16_t v = sample(x + i, y);
                    p[x / 4 * 5 + i] = v >> 2;
                    p[x / 4 * 5 + 4] |= (v & 3) << (2 * i);
                }
            } else for (int i = 0; i < 4; ++i) {
                uint16_t v = sample(x + i, y);
                p[(x + i) * 2] = v; p[(x + i) * 2 + 1] = v >> 8;
            }
        }
        std::vector<uint16_t> pixels;
        auto d = photon::prepareRaw(raw.data(), raw.size(), width, height, stride,
                raw10, top, rows, bin, cfa, codec, pixels);
        for (unsigned y = 0; y < d.height; ++y) for (unsigned x = 0; x < d.width; ++x) {
            int sx = bin ? x / 2 * 4 + x % 2 : x, sy = (bin ? y / 2 * 4 + y % 2 : y) + top;
            uint16_t expected = bin ? (uint32_t(sample(sx, sy)) + sample(sx + 2, sy)
                    + sample(sx, sy + 2) + sample(sx + 2, sy + 2) + 2) / 4 : sample(sx, sy);
            check(pixels[y * d.width + x] == expected, "RAW conversion changed sample codes");
        }
        bool rejected = false;
        try { photon::prepareRaw(raw.data(), raw.size() - 1, width, height, stride,
                raw10, top, rows, bin, cfa, codec, pixels); }
        catch (const std::invalid_argument&) { rejected = true; }
        check(rejected, "truncated plane accepted");
        std::string stem = std::string(argv[1]) + "/" + std::to_string(codec) + "_"
                + std::to_string(cfa) + "_" + std::to_string(raw10) + "_" + std::to_string(bin);
        std::ofstream expected(stem + ".raw", std::ios::binary);
        expected.write(reinterpret_cast<const char*>(pixels.data()), pixels.size() * 2);
        expected.close();
        int fd = open((stem + ".craw").c_str(), O_CREAT | O_TRUNC | O_WRONLY, 0600);
        check(fd >= 0, "open failed");
        craw::Writer writer(fd, "{\"generator\":\"PhotonCamera integration test\"}");
        close(fd); // writer must own a duplicate
        std::vector<uint8_t> encoded(pixels.size() * 3 + 4096);
        auto length = craw::encodeFrame(pixels.data(), d.width, d, encoded.data(), encoded.size());
        float gains[16];
        for (int i = 0; i < 16; ++i) gains[i] = 1.f + i / 16.f;
        writer.addShading(0, 7, 2, 2, gains);
        writer.addEncodedFrame(encoded.data(), length, 0, "{\"lshd_ref\":7,\"exposure_ns\":1000000}");
        writer.addEncodedFrame(encoded.data(), length, 33333333, "{\"lshd_ref\":7}");
        rejected = false;
        try { writer.addEncodedFrame(encoded.data(), length, 33333333); }
        catch (const std::logic_error&) { rejected = true; }
        check(rejected && writer.frameCount() == 2, "frame ordering not enforced");
        int16_t pcm[] = {-32768, 32767, -2, 3};
        writer.addAudio(1234, craw::AudioFormat::S16, 2, 48000, pcm, 2);
        craw::ImuSample gyro[] = {{1000, 1, 2, 3}, {2000, 4, 5, 6}};
        craw::ImuSample accel[] = {{1500, 7, 8, 9}};
        writer.addImu(craw::ImuSensor::Gyroscope, gyro, 2);
        writer.addImu(craw::ImuSensor::Accelerometer, accel, 1);
        writer.close(); writer.close();
    }
    // Constructor failure must throw, without leaking a duplicate descriptor.
    int fd = open("/dev/full", O_WRONLY);
    if (fd >= 0) {
        bool rejected = false;
        try { craw::Writer writer(fd, "{}"); writer.close(); }
        catch (const std::runtime_error&) { rejected = true; }
        close(fd);
        check(rejected, "write failure not reported");
    }
    std::cout << "32 RAW10/RAW16, CFA, crop/bin, codec and container cases passed\n";
}
