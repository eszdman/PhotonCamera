precision highp float;
precision highp int;
layout(rgba16f, binding = 0) uniform highp readonly image2D inTexture;
layout(rgba16f, binding = 2) uniform highp writeonly image2D outTexture;
#define LAYOUT //
#define OFFSET 0,0
#define OUTSET 0,0
#define FIRST 0
#define RESCALING 1
#define TILE 3
#define SQRT2 1.0
#import rescale
#import gaussian
#import median
LAYOUT
void main() {
    ivec2 xyIn = ivec2(gl_GlobalInvocationID.xy);
    ivec2 xyOut = xyIn*RESCALING*TILE + ivec2(OFFSET);//= rescaleUpi(xyIn,ivec4(OFFSET,OUTSET),RESCALING*TILE);
    xyIn = xyIn*RESCALING*TILE + ivec2(OFFSET);//= rescaleUpi(xyIn,ivec4(OFFSET,OUTSET),RESCALING*TILE);
    if(xyOut.x+TILE*RESCALING >= ivec2(OUTSET).x) return;
    if(xyOut.y+TILE*RESCALING >= ivec2(OUTSET).y) return;
    vec4 texColor[TILE*TILE];
    vec4 in0;
    float sum = 0.0;
    for(int i =0; i<TILE*TILE;i++){
        texColor[i] = imageLoad(inTexture,xyIn+ivec2(i%TILE,i/TILE)*RESCALING);
        in0+=texColor[i];
        sum+=1.0;
    }
    in0/=sum;
    //in0 =median9(texColor); //sort[2]/sums[2];

    imageStore(outTexture, xyOut+ivec2(0,0)*RESCALING, in0);
    for(int i =1; i<TILE*TILE;i++){
        imageStore(outTexture, xyOut+ivec2(i%TILE,i/TILE)*RESCALING, (texColor[i]-in0));
    }


    /*
    imageStore(outTexture, xyOut+ivec2(0,0)*RESCALING, (texColor[0]+texColor[1]+texColor[2]+texColor[3]));
    imageStore(outTexture, xyOut+ivec2(1,0)*RESCALING, (texColor[0]+texColor[1]-texColor[2]-texColor[3]));
    imageStore(outTexture, xyOut+ivec2(0,1)*RESCALING, (texColor[0]*SQRT2-texColor[1]*SQRT2));
    imageStore(outTexture, xyOut+ivec2(1,1)*RESCALING, (texColor[2]*SQRT2-texColor[*SQRT2));
    /*
    imageStore(outTexture, xyOut+ivec2(0,0)*RESCALING, (texColor[0]+texColor[1]+texColor[2]*SQRT2));
    imageStore(outTexture, xyOut+ivec2(1,0)*RESCALING, (texColor[0]+texColor[1]-texColor[2]*SQRT2));
    imageStore(outTexture, xyOut+ivec2(0,1)*RESCALING, (texColor[0]-texColor[1]+texColor[3]*SQRT2));
    imageStore(outTexture, xyOut+ivec2(1,1)*RESCALING, (texColor[0]-texColor[1]-texColor[3]*SQRT2));*/

}