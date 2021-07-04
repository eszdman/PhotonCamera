#version 300 es
precision mediump float;
precision mediump sampler2D;
precision mediump isampler2D;
uniform sampler2D BaseFrame;
uniform sampler2D InputFrame;
uniform mat3 HMatrix;
#define distribute(x,dev,sigma) (abs(x-dev))
#define MIN_NOISE 0.1f
#define MAX_NOISE 0.7f
#define TILESIZE 32
#define WEIGHTSIZE 128
#define FRAMECOUNT 15
#define INPUTSIZE 1,1
#import coords
out float Output;
void main() {
    ivec2 xy = ivec2(gl_FragCoord.xy);
    ivec2 xyFrame = ivec2(gl_FragCoord.xy*float(TILESIZE/2));
    vec3 dist = vec3(0.0);
    vec2 align = vec2(xy);

    vec3 in2;
    for (int i=-WEIGHTSIZE/2;i<WEIGHTSIZE/2;i++){
        for (int j=-WEIGHTSIZE/2;j<WEIGHTSIZE/2;j++){

            vec2 inx = vec2(xyFrame+ivec2(i, j));
            //Perspective Warp
            align.x = (inx.x*HMatrix[0][0] + inx.y*HMatrix[0][1] + HMatrix[0][2])/
            (inx.x*HMatrix[2][0] + inx.y*HMatrix[2][1] + HMatrix[2][2]);
            align.y = (inx.x*HMatrix[1][0] + inx.y*HMatrix[1][1] + HMatrix[1][2])/
            (inx.x*HMatrix[2][0] + inx.y*HMatrix[2][1] + HMatrix[2][2]);
            ivec2 aligned = ivec2(align);
            in2 =  texelFetch(InputFrame, mirrorCoords2((aligned), ivec2(INPUTSIZE)), 0).rgb;
            dist+= distribute(texelFetch(BaseFrame, mirrorCoords2((xyFrame+ivec2(i, j)), ivec2(INPUTSIZE)), 0).rgb, in2, 0.1);

            //in2 =             texelFetch(InputFrame, mirrorCoords2((aligned+ivec2(0, i)), ivec2(INPUTSIZE)), 0).rg;
            //dist+= distribute(texelFetch(BaseFrame, mirrorCoords2((xy+ivec2(0, i)), ivec2(INPUTSIZE)), 0).rg, in2, 0.1);
        }
    }
    //dist += ((float(texelFetch(AlignVectors, xy, 0).b)/1024.0));
    Output = ((dist.r+dist.g+dist.b)/float(TILESIZE*TILESIZE*FRAMECOUNT/30));
    Output = 30.0/(Output+0.1);
    if(Output < 10.0){
        Output = 0.0;
    }
    //Output = ((float(texelFetch(AlignVectors, xy, 0).b)/1024.0))/float(FRAMECOUNT) + 0.25;
}
