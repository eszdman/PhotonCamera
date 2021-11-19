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
#import rescale
#import gaussian
float wavepdf(ivec2 xy){
    xy-=ivec2((TILE-1)/2,(TILE-1)/2);
    return pdf(vec2(xy)/2.0);
}
LAYOUT
void main() {
    ivec2 xyIn = ivec2(gl_GlobalInvocationID.xy);
    ivec2 xyOut = xyIn*RESCALING*TILE + ivec2(OFFSET);//= rescaleUpi(xyIn,ivec4(OFFSET,OUTSET),RESCALING*TILE);
    xyIn = xyIn*RESCALING*TILE + ivec2(OFFSET);//= rescaleUpi(xyIn,ivec4(OFFSET,OUTSET),RESCALING*TILE);
    if(xyOut.x+TILE*RESCALING >= ivec2(OUTSET).x) return;
    if(xyOut.y+TILE*RESCALING >= ivec2(OUTSET).y) return;

    vec4 texColor[TILE*TILE];

    for(int i =0; i<TILE*TILE;i++){
        texColor[i] = imageLoad(inTexture,xyIn+ivec2(i%TILE,i/TILE)*RESCALING);
    }

    vec4 in0;

    float sum = 0.0;
    for(int i =0; i<TILE*TILE;i++){
        //float distr = wavepdf(ivec2(i%TILE,i/TILE));
        in0+=texColor[i]*1.0;
        sum+=1.0;
    }
    in0/=sum;

    imageStore(outTexture, xyOut+ivec2(0,0)*RESCALING, in0);
    for(int i =1; i<TILE*TILE;i++){
        imageStore(outTexture, xyOut+ivec2(i%TILE,i/TILE)*RESCALING, in0-texColor[i]);
    }
}