

// If the sensor noise is linear in all channels, then 50/50 split should reduce it the most.
// Rescale to 0.5 total because we are adding two values per weight.
#define RB_WEIGHT 0.25f
#define GG_WEIGHT 0.25f
//#define RB_WEIGHT 0.325f
//#define GG_WEIGHT 0.675f

precision lowp float;

uniform usampler2D frame;

out float result;

void main() {
    ivec2 xy = ivec2(gl_FragCoord.xy) * 2;

    float topLeftAndBottomRight = float(
        texelFetch(frame, xy, 0).x
        + texelFetch(frame, xy + ivec2(1, 1), 0).x
    );

    float topRightAndBottomLeft = float(
        texelFetch(frame, xy + ivec2(1, 0), 0).x
        + texelFetch(frame, xy + ivec2(0, 1), 0).x
    );

    result = RB_WEIGHT * topLeftAndBottomRight
        + GG_WEIGHT * topRightAndBottomLeft;
}
