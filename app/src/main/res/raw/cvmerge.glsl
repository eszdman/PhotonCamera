precision highp float;
precision highp sampler2D;

uniform sampler2D OutputBuffer;
uniform sampler2D InputBuffer;
uniform sampler2D Weights;
uniform sampler2D Weight;

uniform float alignk;
uniform uvec2 rawsize;
uniform int number;
uniform highp mat3 HMatrix;

#define MIN_NOISE 0.1f
#define MAX_NOISE 1.0f
#define TILESIZE (48)
#define MPY (1.0)
#define MIN (1.0)
#define WP 1.0, 1.0, 1.0
#define BAYER 0
#define ROTATION 0.0
#define HDR 0
#define HMAT 0.0
#define FRAMECOUNT 1
#define MATMUL 1000.0

#import coords
#import interpolation
out float Output;
void main() {
    ivec2 xy = ivec2(gl_FragCoord.xy);
    ivec2 workSize = ivec2(textureSize(OutputBuffer, 0));
    ivec2 state = xy%2;

    highp vec2 align = vec2(xy/2);
    highp vec2 inx = vec2(xy/2);
    //mat3 HMatrix = mat3(HMAT);
    //Perspective Warp
    align.x = (inx.x*HMatrix[0][0] + inx.y*HMatrix[0][1] + HMatrix[0][2])/
              (inx.x*HMatrix[2][0] + inx.y*HMatrix[2][1] + HMatrix[2][2]);
    align.y = (inx.x*HMatrix[1][0] + inx.y*HMatrix[1][1] + HMatrix[1][2])/
              (inx.x*HMatrix[2][0] + inx.y*HMatrix[2][1] + HMatrix[2][2]);

    ivec2 state2 = (xy-ivec2(BAYER%2,BAYER/2))%2;
    ivec2 aligned = mirrorCoords2(ivec2(align),ivec2((rawsize)/uint(2))-1)*2 + state;
    float br2 = texelFetch(InputBuffer, aligned, 0).x;
    float minDist = 1000.0;
    float dist = 0.0;
    float br1 = ((texelFetch(OutputBuffer, xy, 0).x));

    ivec2 wSize = ivec2(textureSize(Weights, 0));

    //float sumweights = texelFetch(Weights, mirrorCoords2(xy/(TILESIZE*2),wSize), 0).r;
    //float windoww = texelFetch(Weight, mirrorCoords2(xy/(TILESIZE*2),wSize), 0).r/sumweights;
    float sumweights = textureCubicHardware(Weights,vec2(gl_FragCoord.xy)/vec2(workSize)).r;
    float windoww = textureCubicHardware(Weight,vec2(gl_FragCoord.xy)/vec2(workSize)).r*float(FRAMECOUNT)/sumweights;
    //windoww = 1.0-(clamp((windoww),0.0,1.0)-1.0);
    if(number == 1){
        Output = ((br1 + br2)/2.0)*windoww/float(FRAMECOUNT);
    } else {
        Output = br1 + br2*windoww/float(FRAMECOUNT);
    }
    //Output = mix(br1,br2, windoww/float(number));
    //Output = windoww;
}
