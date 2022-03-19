precision highp float;
layout(rgba16f, binding = 0) uniform highp readonly image2D inTexture;
layout(rgba16f, binding = 1) uniform highp writeonly image2D outTexture;
#define LAYOUT //
#define OUTSET 0,0
#define TILE 3
#define NOISEO 0.0
#define NOISES 0.0
#define COLOR r

LAYOUT
void main() {
    ivec2 xyIn = ivec2(gl_GlobalInvocationID.xy)*TILE;
    if((xyIn.x) + TILE >= ivec2(OUTSET).x) return;
    if((xyIn.y) + TILE >= ivec2(OUTSET).y) return;
    ivec2 maxcoords = ivec2(0,0);
    float maxval = 0.0;
    for(int i =0; i<TILE;i++){
        for(int j =0; j<TILE;j++){
            ivec2 coords = ivec2(i,j);
            float val = imageLoad(inTexture,xyIn+coords).COLOR;
            if(val > maxval){
                maxval = val;
                maxcoords = coords;
            }
        }
    }
    float avr;
        avr = (
        imageLoad(inTexture, xyIn+maxcoords+ivec2(0,-1)).COLOR+
        imageLoad(inTexture, xyIn+maxcoords+ivec2(-1,0)).COLOR+
        imageLoad(inTexture, xyIn+maxcoords+ivec2(0,1)).COLOR+
        imageLoad(inTexture, xyIn+maxcoords+ivec2(1,0)).COLOR)/4.0;
    float noise = sqrt(avr*NOISES + NOISEO);
    if(maxval-avr > noise && maxval/(avr+0.0001) > 7.0){
        vec4 inp = imageLoad(inTexture, xyIn+maxcoords);
        inp.COLOR = avr;
        imageStore(outTexture, xyIn+maxcoords, inp);
    }
}