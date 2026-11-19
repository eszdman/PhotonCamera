#define PACKED 0
#define LAYOUT //
LAYOUT
precision highp float;
precision highp int;
precision highp sampler2D;
uniform sampler2D InputBuffer;
uniform sampler2D EstimateBuffer;
uniform sampler2D MaskBuffer;
uniform sampler2D LocalMaskBuffer;
uniform ivec2 validSize;
uniform int originY;
uniform int outputY;
uniform int outputRows;
uniform float amount;
uniform int useMask;
uniform float clipLevel;
layout(rgba16f, binding = 0) writeonly uniform highp image2D OutputBuffer;
const vec3 LUMA = vec3(0.212671, 0.715160, 0.072169);
vec3 rgbAt(ivec2 p) {
    return texelFetch(InputBuffer, clamp(p, ivec2(0), textureSize(InputBuffer, 0) - 1), 0).rgb;
}
float scalarAt(sampler2D plane, ivec2 p) {
#if PACKED
    return texelFetch(plane,ivec2(p.x/4,p.y),0)[p.x%4];
#else
    return texelFetch(plane,p,0).r;
#endif
}
void main() {
    ivec2 local = ivec2(gl_GlobalInvocationID.xy);
    if (local.x >= textureSize(InputBuffer, 0).x || local.y >= outputRows) return;
    ivec2 p = local + ivec2(0, outputY);
    vec4 original = texelFetch(InputBuffer, p, 0);
    float blend = amount;
    if (useMask != 0) {
        ivec2 q = p - ivec2(0, originY);
        float smoothed = 0.0;
        smoothed += 0.0022181959 * scalarAt(MaskBuffer, clamp(q + ivec2(0,-6), ivec2(0), validSize - 1));
        smoothed += 0.0087731348 * scalarAt(MaskBuffer, clamp(q + ivec2(0,-5), ivec2(0), validSize - 1));
        smoothed += 0.0270231576 * scalarAt(MaskBuffer, clamp(q + ivec2(0,-4), ivec2(0), validSize - 1));
        smoothed += 0.0648251851 * scalarAt(MaskBuffer, clamp(q + ivec2(0,-3), ivec2(0), validSize - 1));
        smoothed += 0.1211093901 * scalarAt(MaskBuffer, clamp(q + ivec2(0,-2), ivec2(0), validSize - 1));
        smoothed += 0.1762131228 * scalarAt(MaskBuffer, clamp(q + ivec2(0,-1), ivec2(0), validSize - 1));
        smoothed += 0.1996756275 * scalarAt(MaskBuffer, clamp(q + ivec2(0,0), ivec2(0), validSize - 1));
        smoothed += 0.1762131228 * scalarAt(MaskBuffer, clamp(q + ivec2(0,1), ivec2(0), validSize - 1));
        smoothed += 0.1211093901 * scalarAt(MaskBuffer, clamp(q + ivec2(0,2), ivec2(0), validSize - 1));
        smoothed += 0.0648251851 * scalarAt(MaskBuffer, clamp(q + ivec2(0,3), ivec2(0), validSize - 1));
        smoothed += 0.0270231576 * scalarAt(MaskBuffer, clamp(q + ivec2(0,4), ivec2(0), validSize - 1));
        smoothed += 0.0087731348 * scalarAt(MaskBuffer, clamp(q + ivec2(0,5), ivec2(0), validSize - 1));
        smoothed += 0.0022181959 * scalarAt(MaskBuffer, clamp(q + ivec2(0,6), ivec2(0), validSize - 1));
        // Smoothing may soften a mask, but must not re-enable rejected pixels.
        blend *= min(smoothed, scalarAt(LocalMaskBuffer, q));
    }
    // Dilate the clipping mask to protect the neighbourhood of saturated sites.
    for (int y = -2; y <= 2; y++) for (int x = -2; x <= 2; x++) {
        if (x*x + y*y > 5) continue;
        vec3 rgb = rgbAt(p + ivec2(x,y));
        if (max(rgb.r, max(rgb.g, rgb.b)) >= 0.95 * clipLevel) blend = 0.0;
    }
    float before = dot(original.rgb, LUMA);
    float after = scalarAt(EstimateBuffer, p - ivec2(0, originY));
    float gain = before > 1e-7 ? mix(1.0, after / before, blend) : 1.0;
    imageStore(OutputBuffer, p, vec4(original.rgb * gain, original.a));
}
