precision highp float;
precision highp int;
layout(rgba16f, binding = 0) uniform highp readonly image2D inTexture;
layout(rgba16f, binding = 1) uniform highp writeonly image2D outTexture;
#define INSIZE 1,1
#define LAYOUT //
LAYOUT
void main() {
    ivec2 xy = ivec2(gl_GlobalInvocationID.xy);
    vec4 Output;
    if(xy.x < 2 || xy.y < 2 || xy.x > ivec2(INSIZE).x-2 || xy.y > ivec2(INSIZE).y-2) return;
    ivec2 shift = xy%2;
    if(shift.x+shift.y ==  1){
        Output.x = imageLoad(inTexture, xy-ivec2(-1,1)).x-imageLoad(inTexture, xy+ivec2(-1,1)).x;
        Output.y = imageLoad(inTexture, xy-ivec2(1,1)).x-imageLoad(inTexture, xy+ivec2(1,1)).x;
    } else {
        Output.x = imageLoad(inTexture, xy-ivec2(2,0)).x-imageLoad(inTexture, xy+ivec2(2,0)).x;
        Output.y = imageLoad(inTexture, xy-ivec2(0,2)).x-imageLoad(inTexture, xy+ivec2(0,2)).x;
    }

    Output/=2.0;
    imageStore(outTexture, xy, Output);
}
