//
// Created by eszdman on 21.11.2026.
//

// Some mobile Vulkan drivers flush binary16 subnormals during pack/unpack.
// AMaZE stores 1e-5 gradient weights in FP16 and divides by them in the next pass.
uint halfBits(float value, uint hardware) {
    if (abs(value) >= 0.00006103515625 || isnan(value)) return hardware;
    return ((floatBitsToUint(value) >> 16) & 0x8000u) | uint(roundEven(abs(value) * 16777216.0));
}
uint storeHalfPair(vec2 value) {
    uint hw = packHalf2x16(value);
    return halfBits(value.x, hw & 65535u) | (halfBits(value.y, hw >> 16) << 16);
}
float halfValue(uint bits, float hardware) {
    if ((bits & 0x7c00u) != 0u) return hardware;
    float magnitude = float(bits & 1023u) / 16777216.0;
    return (bits & 32768u) == 0u ? magnitude : -magnitude;
}
vec2 loadHalfPair(uint bits) {
    vec2 hw = unpackHalf2x16(bits);
    return vec2(halfValue(bits & 65535u, hw.x), halfValue(bits >> 16, hw.y));
}
