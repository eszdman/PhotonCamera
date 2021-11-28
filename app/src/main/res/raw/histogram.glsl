precision highp int;
layout(rgba8, binding = 0) readonly uniform highp image2D inTexture;
layout(std430, binding = 1) buffer histogramRed {
    uint reds[];
};
layout(std430, binding = 2) buffer histogramGreen {
    uint greens[];
};
layout(std430, binding = 3) buffer histogramBlue {
    uint blues[];
};
shared vec3 denoise;
#define HISTSIZE 255.0
#define SCALE 1
#define LAYOUT //
LAYOUT
void main() {
    ivec2 storePos = ivec2(gl_GlobalInvocationID.xy)*SCALE;
    ivec2 imgsize = imageSize(inTexture).xy;
    if (storePos.x < imgsize.x && storePos.y < imgsize.y) {
        vec3 texColor = imageLoad(inTexture, storePos).rgb;
        uint red = uint(texColor.r * HISTSIZE);
        uint green = uint(texColor.g * HISTSIZE);
        uint blue = uint(texColor.b * HISTSIZE);
        atomicAdd(reds[red], 1u);
        atomicAdd(greens[green], 1u);
        atomicAdd(blues[blue], 1u);
    }
}