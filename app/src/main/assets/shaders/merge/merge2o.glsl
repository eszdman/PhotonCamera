precision highp float;
precision highp sampler2D;
uniform highp sampler2D inTexture;
uniform highp sampler2D alignmentTexture;
uniform int yOffset;
// Sensor red-site offset ((cfa%2, cfa/2)); passed as a uniform because GLProg
// clears its define list after every program load, so a CFAPATTERN define set
// once at pipeline start would never reach this late-bound shader.
uniform ivec2 cfaShift;
#define TILE 2
#define CONCAT 1
out float Output;

// The merged base is already normalized fp16 (rgba16f, clamped to [0,1] by
// the merge stages); this pass only unpacks the 2x2 quads back onto the raw
// Bayer grid. No white-level re-encode or quantization dither is needed - the
// output pipeline renders straight into an R16F buffer.

void main() {
    ivec2 xy = ivec2(gl_FragCoord.xy);
    xy += ivec2(0, yOffset);
    // Undo the merge00 packing shift: real raw site X lives at packed
    // rel = X + cfaShift (merge00 shifted quad origins by -cfaShift and
    // filled the out-of-range sites with edge duplicates, which land at
    // rel < cfaShift and rel > rawSize-1 and are simply never read here;
    // cfaShift is zero for RGGB/BGGR, so this is the identity for them).
    ivec2 rel = xy + cfaShift;
    vec4 bayer = texelFetch(inTexture, rel / TILE, 0);
    Output = clamp(bayer[(rel.x & 1) + (rel.y & 1) * TILE], 0.0, 1.0);
}
