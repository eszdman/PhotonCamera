precision highp sampler2D;
precision highp int;
precision highp float;
uniform sampler2D inTexture;
uniform vec4 exposure;
// input1 = display-domain luma-Laplacian magnitude mapped to the top bin
// (AutoExposureCurve's "LLF detail scan range"). input2 unused.
uniform float input1;
uniform float input2;
#define COL_R 1
#define COL_G 0
#define COL_B 0
#define COL_A 0
#define COL_CUSTOM 0
#define HISTSIZE 64
#define SCALE 3
#define HISTSTEPS uint(HISTSIZE/64)

#if COL_R == 1
layout(std430, binding = 1) buffer histogramRed {
    uint reds[];
};
shared uint localRed[HISTSIZE];
#endif
#if COL_G == 1
layout(std430, binding = 2) buffer histogramGreen {
    uint greens[];
};
shared uint localGreen[HISTSIZE];
#endif
#if COL_B == 1
layout(std430, binding = 3) buffer histogramBlue {
    uint blues[];
};
shared uint localBlue[HISTSIZE];
#endif
#if COL_A == 1
layout(std430, binding = 4) buffer histogramAlpha {
    uint alphas[];
};
shared uint localAlpha[HISTSIZE];
#endif

#define CUSTOM_PROGRAM //
#ifndef SPATIAL_KERNEL
#define SPATIAL_KERNEL 0
#endif
#define LAYOUT //
LAYOUT

// sRGB OETF without the upper clamp, matching AutoExposureCurve's extended
// encode: the local-contrast magnitude is measured in the display domain the
// tone output lives in, so a fixed threshold means the same amount of visible
// detail at any scene brightness.
float oetfExtended(float x) {
    x = max(x, 0.0);
    return x <= 0.0031308 ? x * 12.92 : 1.055 * pow(x, 1.0 / 2.4) - 0.055;
}

float lumaAt(ivec2 p, ivec2 size) {
    vec3 c = texelFetch(inTexture, clamp(p, ivec2(0), size - ivec2(1)), 0).rgb;
    return dot(vec3(oetfExtended(c.r), oetfExtended(c.g), oetfExtended(c.b)),
            vec3(0.299, 0.587, 0.114));
}

// Local-detail histogram: |4*L - sum(neighbours)| of display-encoded luma per
// sampled pixel, binned by magnitude / input1. Consumers threshold the CDF to
// get the fraction of the image that already carries local detail.
void main() {
    ivec2 gid = ivec2(gl_GlobalInvocationID.xy);
    ivec2 imgsize = textureSize(inTexture, 0).xy;
    ivec2 storePos = gid * SCALE;
    uint index = uint(gl_LocalInvocationIndex) * HISTSTEPS;
    #if COL_R == 1
    for (uint i = 0u; i < HISTSTEPS; i++) {
        localRed[index + i] = 0u;
    }
    #endif
    barrier();

    if (storePos.x < imgsize.x && storePos.y < imgsize.y) {
        float c = lumaAt(storePos, imgsize);
        float l = lumaAt(storePos + ivec2(-1, 0), imgsize);
        float r = lumaAt(storePos + ivec2(1, 0), imgsize);
        float u = lumaAt(storePos + ivec2(0, -1), imgsize);
        float d = lumaAt(storePos + ivec2(0, 1), imgsize);
        float mag = abs(4.0 * c - l - r - u - d);
        float range = max(input1, 1.0e-4);
        uint bin = uint(clamp(mag / range, 0.0, 1.0) * float(HISTSIZE - 1) + 0.5);
        #if COL_R == 1
        atomicAdd(localRed[bin], 1u);
        #endif
    }
    barrier();

    #if COL_R == 1
    for (uint i = 0u; i < HISTSTEPS; i++) {
        atomicAdd(reds[index + i], localRed[index + i]);
    }
    #endif
}
