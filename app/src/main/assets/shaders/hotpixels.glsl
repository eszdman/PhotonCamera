precision highp float;
precision highp int;
layout(rgba16f, binding = 0) uniform highp readonly image2D inTexture;
layout(rgba16f, binding = 1) uniform highp writeonly image2D outTexture;
#define LAYOUT //
#define OUTSET 0,0
#define TILE 3
#define NOISEO 0.0
#define NOISES 0.0
#define IMPULSE 7.0
//Stage 0
//R| G  R| G
//G  B  G  B
//R| G  R| G
//G  B  G  B

//Stage 1
//R  G  R  G
//G  B| G  B|
//R  G  R  G
//G  B| G  B|


//Stage 2
//R  G| R  G|
//G| B  G  B
//R  G| R  G|
//G| B  G| B
#import median
LAYOUT
void main() {
    ivec2 xyIn = ivec2(gl_GlobalInvocationID.xy)*TILE*2;
    int z = int(gl_GlobalInvocationID.z);
    ivec2 shift = ivec2(z%2,z%2);
    if((xyIn.x)+shift.x + TILE*2 >= ivec2(OUTSET).x) return;
    if((xyIn.y)+shift.y + TILE*2 >= ivec2(OUTSET).y) return;
    ivec2 maxcoords = ivec2(0,0);
    float maxval = 0.0;
    for(int i =0; i<TILE;i++){
        for(int j =0; j<TILE;j++){
            if(z != 2){
                ivec2 coords = +ivec2(i,j)*2+shift;
                float val = imageLoad(inTexture,xyIn+coords).r;
                if(val > maxval){
                    maxval = val;
                    maxcoords = coords;
                }
            } else {
                if((i+j)%2 == 0) continue;
                ivec2 coords = +ivec2(i,j);
                float val = imageLoad(inTexture,xyIn+coords).r;
                if(val > maxval){
                    maxval = val;
                    maxcoords = coords;
                }
            }

        }
    }
    float avr[5];
    if(z != 2){
        avr[1] = imageLoad(inTexture, xyIn+maxcoords+ivec2(0,-1)*2).r;
        avr[2] = imageLoad(inTexture, xyIn+maxcoords+ivec2(-1,0)*2).r;
        avr[3] = imageLoad(inTexture, xyIn+maxcoords+ivec2(0,1)*2).r;
        avr[4] = imageLoad(inTexture, xyIn+maxcoords+ivec2(1,0)*2).r;
        avr[0] = (avr[1]+avr[2]+avr[3]+avr[4])/4.0;
    } else {
        avr[1] = imageLoad(inTexture, xyIn+maxcoords+ivec2(-1,-1)*2).r;
        avr[2] = imageLoad(inTexture, xyIn+maxcoords+ivec2(-1,1)*2).r;
        avr[3] = imageLoad(inTexture, xyIn+maxcoords+ivec2(1,-1)*2).r;
        avr[4] = imageLoad(inTexture, xyIn+maxcoords+ivec2(1,1)*2).r;
        avr[0] = (avr[1]+avr[2]+avr[3]+avr[4])/4.0;
    }

    float noise = sqrt(avr[0]*NOISES + NOISEO);
    avr[0] = median5(avr);
    if(maxval/(avr[0]+0.0001) > IMPULSE
    || avr[0]/(maxval+0.0001) > IMPULSE
    )
    imageStore(outTexture, xyIn+maxcoords, vec4(avr[0]));
}