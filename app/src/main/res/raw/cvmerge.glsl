#version 300 es
precision highp float;
precision highp sampler2D;

uniform sampler2D OutputBuffer;
uniform sampler2D InputBuffer;

uniform int yOffset;
uniform float alignk;
uniform uvec2 rawsize;
uniform int number;
uniform mat3 HMatrix;

#define MIN_NOISE 0.1f
#define MAX_NOISE 1.0f
#define TILESIZE (48)
#define MPY (1.0)
#define MIN (1.0)
#define WP 1.0, 1.0, 1.0
#define BAYER 0
#define ROTATION 0.0
#define HDR 0

#import coords
out float Output;
void main() {
    ivec2 xy = ivec2(gl_FragCoord.xy);
    xy+=ivec2(0,yOffset);
    ivec2 state = xy%2;

    vec2 align = vec2(xy/2);
    //Perspective Warp
    align.x = (align.x*HMatrix[0][0] + align.y*HMatrix[0][1] + HMatrix[0][2])/
              (align.x*HMatrix[2][0] + align.y*HMatrix[2][1] + HMatrix[2][2]);
    align.y = (align.x*HMatrix[1][0] + align.y*HMatrix[1][1] + HMatrix[1][2])/
              (align.x*HMatrix[2][0] + align.y*HMatrix[2][1] + HMatrix[2][2]);

    ivec2 state2 = (xy-ivec2(BAYER%2,BAYER/2))%2;
    float br2 = texelFetch(InputBuffer, mirrorCoords2(ivec2(align),ivec2((rawsize)/uint(2))-1)*2 + state, 0).x;
    float minDist = 1000.0;
    float dist = 0.0;
    float br1 = ((texelFetch(OutputBuffer, xy, 0).x));

    Output = mix(br1,br2, 1.0/float(number));
}
