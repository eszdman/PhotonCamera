#define PACKED 0
#define LAYOUT //
LAYOUT
precision highp float;
precision highp int;
precision highp sampler2D;
uniform sampler2D InputBuffer;
uniform int originY;
uniform ivec2 validSize;
uniform float contrastRatio;
uniform vec2 noiseModel;
uniform float clipLevel;
layout(rgba16f, binding = 0) writeonly uniform highp image2D OutputBuffer;
const vec3 LUMA = vec3(0.212671, 0.715160, 0.072169);
vec3 rgbAt(ivec2 p) {
    return texelFetch(InputBuffer, clamp(p, ivec2(0), textureSize(InputBuffer, 0) - 1), 0).rgb;
}
float luminanceAt(ivec2 p) {
    return max(dot(rgbAt(p), LUMA), 0.0);
}
float maskAt(ivec2 p) {
    if (contrastRatio >= 1.0) return 0.0;
    vec4 positive = vec4(luminanceAt(p+ivec2(1,0)), luminanceAt(p+ivec2(0,1)),
                          luminanceAt(p+ivec2(2,0)), luminanceAt(p+ivec2(0,2)));
    vec4 negative = vec4(luminanceAt(p-ivec2(1,0)), luminanceAt(p-ivec2(0,1)),
                          luminanceAt(p-ivec2(2,0)), luminanceAt(p-ivec2(0,2)));
    vec4 gradient = positive - negative;
    float energy = dot(gradient, gradient);
    // Each gradient is a difference of two samples: add their variances.
    // The scalar model deliberately avoids assuming independent RGB channels
    // after demosaicing, which would underestimate correlated luminance noise.
    float noiseEnergy = noiseModel.x * dot(positive + negative, vec4(1.0)) + 8.0 * noiseModel.y;
    float confidence = max(energy - noiseEnergy, 0.0) / max(energy + noiseEnergy, 1e-20);
    float blend = smoothstep(contrastRatio, contrastRatio + 0.5*(1.0-contrastRatio), confidence);
    // Dilate the clipping mask to protect the neighbourhood of saturated sites.
    for (int y = -2; y <= 2; y++) for (int x = -2; x <= 2; x++) {
        if (x*x + y*y > 5) continue;
        vec3 rgb = rgbAt(p + ivec2(x,y));
        if (max(rgb.r, max(rgb.g, rgb.b)) >= 0.95 * clipLevel) blend = 0.0;
    }
    return blend;
}
void main() {
    ivec2 local=ivec2(gl_GlobalInvocationID.xy);
#if PACKED
    ivec2 p=ivec2(local.x*4,local.y);
    if(any(greaterThanEqual(p,validSize))) return;
    p.y+=originY;
    imageStore(OutputBuffer,local,vec4(maskAt(p),maskAt(p+ivec2(1,0)),maskAt(p+ivec2(2,0)),maskAt(p+ivec2(3,0))));
#else
    if(any(greaterThanEqual(local,validSize))) return;
    imageStore(OutputBuffer,local,vec4(maskAt(local+ivec2(0,originY))));
#endif
}
