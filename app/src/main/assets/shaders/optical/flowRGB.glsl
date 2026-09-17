#define LAYOUT //
LAYOUT
precision highp float;
precision highp sampler2D;
precision highp image2D;
// Normalized fp16 raw input (Allocator.createF16 pre-normalizes on the CPU).
uniform highp sampler2D inTexture;
layout(rgba16f, binding = 0) uniform highp writeonly image2D outTexture;

// Produces the RGB frames fed to the FlowNet optical flow model (512x384).
// Each output texel stores (B, G, R, 255) scaled to [0,255], channel order
// matching the desktop *PIXEL_BGR* runner so the ncnn net sees the same layout
// it was validated with.
uniform float exposure;
uniform int cfaPattern;
uniform ivec2 rawHalf;     // rawSize/2 (a 2x2 camera quad == one texture texel)
uniform vec2 flowScale;    // model pixel -> quad texel scale (rawHalf/FLOW)

float getBayer(ivec2 coords, highp sampler2D tex) {
    return float(texelFetch(tex, coords, 0).r);
}

// One 2x2 camera quad -> RGB according to the CFA layout.
// q0=(x,y)  q1=(x+1,y)  q2=(x,y+1)  q3=(x+1,y+1)  in *quad* (rawHalf) coords.
vec3 quadToRGB(int px, int py, highp sampler2D tex) {
    vec3 rgb;
    if (cfaPattern == 0) {         // RGGB
        rgb = vec3(getBayer(ivec2(2*px + 0, 2*py + 0), tex),
                   getBayer(ivec2(2*px + 1, 2*py + 0), tex),
                   getBayer(ivec2(2*px + 1, 2*py + 1), tex));
    } else if (cfaPattern == 1) {  // GRBG
        rgb = vec3(getBayer(ivec2(2*px + 1, 2*py + 0), tex),
                   (getBayer(ivec2(2*px + 0, 2*py + 0), tex)+getBayer(ivec2(2*px + 1, 2*py + 1), tex))*0.5,
                   getBayer(ivec2(2*px + 0, 2*py + 1), tex));
    } else if (cfaPattern == 2) {  // GBRG
        rgb = vec3(getBayer(ivec2(2*px + 1, 2*py + 1), tex),
                   (getBayer(ivec2(2*px + 0, 2*py + 0), tex)+getBayer(ivec2(2*px + 1, 2*py + 1), tex))*0.5,
                   getBayer(ivec2(2*px + 1, 2*py + 0), tex));
    } else {                       // BGGR
        rgb = vec3(getBayer(ivec2(2*px + 1, 2*py + 1), tex),
                   (getBayer(ivec2(2*px + 0, 2*py + 1), tex)+getBayer(ivec2(2*px + 1, 2*py + 0), tex))*0.5,
                   getBayer(ivec2(2*px + 0, 2*py + 0), tex));
    }
    return rgb;
}

// Bilinear blend of 4 quads at (u,v) (fractional quad coordinates), which
// low-passes the bayer data while downscaling.
vec3 sampleRGB(vec2 uv, highp sampler2D tex) {
    ivec2 maxQ = rawHalf - 1;
    ivec2 i0 = ivec2(floor(uv));
    vec2 f = uv - vec2(i0);
    i0 = clamp(i0, ivec2(0), maxQ - 1);
    vec3 c00 = quadToRGB(i0.x,     i0.y,     tex);
    vec3 c10 = quadToRGB(i0.x + 1, i0.y,     tex);
    vec3 c01 = quadToRGB(i0.x,     i0.y + 1, tex);
    vec3 c11 = quadToRGB(i0.x + 1, i0.y + 1, tex);
    vec3 a = mix(c00, c10, f.x);
    vec3 b = mix(c01, c11, f.x);
    return mix(a, b, f.y);
}

void main() {
    ivec2 outSize = imageSize(outTexture);
    ivec2 xy = ivec2(gl_GlobalInvocationID.xy);
    if (xy.x >= outSize.x || xy.y >= outSize.y) return;

    // Map the model output texel onto the bayer grid. The full frame is
    // stretched into the fixed model resolution (per-axis scale); the flow
    // stored at each texel is multiplied back by the same factors on the CPU
    // side, so the merge shader samples it with plain normalized uv.
    vec2 pos = (vec2(xy) + vec2(0.5)) * flowScale;
    vec3 rgb = sampleRGB(pos, inTexture);

    vec3 scaled = clamp(clamp(rgb, 0.0, 1.0) * vec3(exposure) * 255.0, 0.0, 255.0);
    imageStore(outTexture, xy, vec4(scaled.b, scaled.g, scaled.r, 255.0));
}
