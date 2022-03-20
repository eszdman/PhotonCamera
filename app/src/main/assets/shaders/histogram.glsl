precision mediump sampler2D;
precision highp int;
uniform sampler2D inTexture;
#define COL_R 1
#define COL_G 1
#define COL_B 1
#define COL_A 1

#if COL_R == 1
layout(std430, binding = 1) buffer histogramRed {
    uint reds[];
};
#endif
#if COL_G == 1
layout(std430, binding = 2) buffer histogramGreen {
    uint greens[];
};
#endif
#if COL_B == 1
layout(std430, binding = 3) buffer histogramBlue {
    uint blues[];
};
#endif
#if COL_A == 1
layout(std430, binding = 4) buffer histogramAlpha {
    uint alphas[];
};
#endif
#define HISTSIZE 255.0
#define SCALE 1
#define LAYOUT //
LAYOUT
void main() {
    ivec2 storePos = ivec2(gl_GlobalInvocationID.xy)*SCALE;
    ivec2 imgsize = textureSize(inTexture,0).xy;
    if (storePos.x < imgsize.x && storePos.y < imgsize.y) {
        vec4 texColor = texture(inTexture,(vec2(storePos) + 0.5)/vec2(imgsize));
        #if COL_R == 1
        atomicAdd(reds[uint(texColor.r * HISTSIZE)], 1u);
        #endif
        #if COL_G == 1
        atomicAdd(greens[uint(texColor.g * HISTSIZE)], 1u);
        #endif
        #if COL_B == 1
        atomicAdd(blues[uint(texColor.b * HISTSIZE)], 1u);
        #endif
        #if COL_A == 1
        atomicAdd(alphas[uint(texColor.a * HISTSIZE)], 1u);
        #endif
    }
}