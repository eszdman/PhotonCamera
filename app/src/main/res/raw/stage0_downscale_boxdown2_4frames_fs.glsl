#version 300 es

// If the sensor noise is linear in all channels, then 50/50 split should reduce it the most.
// Rescale to 0.5 total because we are adding two values per weight.
#define RB_WEIGHT 0.25f
#define GG_WEIGHT 0.25f
//#define RB_WEIGHT 0.325f
//#define GG_WEIGHT 0.675f

precision lowp float;

uniform usampler2D frame1;
uniform usampler2D frame2;
uniform usampler2D frame3;
uniform usampler2D frame4;

out vec4 result;

vec2 getValForTex(in usampler2D tex, ivec2 xy) {
    float topLeftAndBottomRight = float(
        texelFetch(tex, xy, 0).x
        + texelFetch(tex, xy + ivec2(1, 1), 0).x
    );
    float topRightAndBottomLeft = float(
        texelFetch(tex, xy + ivec2(1, 0), 0).x
        + texelFetch(tex, xy + ivec2(0, 1), 0).x
    );
    return vec2(topLeftAndBottomRight, topRightAndBottomLeft);
}

void main() {
    ivec2 xy = ivec2(gl_FragCoord.xy) * 2;

    vec2 val1 = getValForTex(frame1, xy);
    vec2 val2 = getValForTex(frame2, xy);
    vec2 val3 = getValForTex(frame3, xy);
    vec2 val4 = getValForTex(frame4, xy);

    result = RB_WEIGHT * vec4(val1.x, val2.x, val3.x, val4.x)
        + GG_WEIGHT * vec4(val1.y, val2.y, val3.y, val4.y);
}
