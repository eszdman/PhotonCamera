precision highp float;
precision highp int;
layout(rgba16f, binding = 0) uniform highp readonly image2D inTexture;
layout(rgba16f, binding = 2) uniform highp writeonly image2D outTexture;
#define NOISEO 0.0
#define NOISES 0.0
#define LAYOUT //
#define OUTSET 0,0
#define RESCALING 1
#define TILE 3
#import thresholding
LAYOUT
void main() {
    ivec2 xyIn = ivec2(gl_GlobalInvocationID.xy);
    if(xyIn.x >= ivec2(OUTSET).x) return;
    if(xyIn.y >= ivec2(OUTSET).y) return;

    vec4 inp = imageLoad(inTexture,xyIn);

    if(xyIn%(TILE*RESCALING) != ivec2(0,0)){
        imageStore(outTexture, xyIn, hardThresholding(inp,sqrt(abs(1.0*NOISES + NOISEO))));
    }
}