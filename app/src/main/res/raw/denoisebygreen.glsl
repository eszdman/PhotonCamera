#version 300 es
precision highp float;
precision mediump sampler2D;
uniform sampler2D RawBuffer;
uniform sampler2D GreenBuffer;
out float Output;
#define sharpen (-0.45)
//#define sharpen (-0.0)
#define mainv (1.0)
#define greenmin (0.04)
#define greenmax (0.7)
void main() {
    ivec2 xy = ivec2(gl_FragCoord.xy);
    int fact1 = xy.x%2;
    int fact2 = xy.y%2;
    if(fact1+fact2 != 1){
        float g[9];
        g[0] = (texelFetch(GreenBuffer, (xy+ivec2(0,0)), 0).x);
        g[1] = (texelFetch(GreenBuffer, (xy+ivec2(-2,0)), 0).x);
        g[2] = (texelFetch(GreenBuffer, (xy+ivec2(0,-2)), 0).x);
        g[3] = (texelFetch(GreenBuffer, (xy+ivec2(2,0)), 0).x);
        g[4] = (texelFetch(GreenBuffer, (xy+ivec2(0,2)), 0).x);

        g[5] = (texelFetch(GreenBuffer, (xy+ivec2(-2,-2)), 0).x);
        g[6] = (texelFetch(GreenBuffer, (xy+ivec2(2,2)), 0).x);
        g[7] = (texelFetch(GreenBuffer, (xy+ivec2(-2,2)), 0).x);
        g[8] = (texelFetch(GreenBuffer, (xy+ivec2(2,-2)), 0).x);
        bool right = true;
        for(int i=0; i<9;i++){
            if(g[i] > greenmax) {
                right=false;
                break;
            }
        }
        if((g[0]+g[1]+g[2]+g[3]+g[4])*0.9 > g[5]+g[6]+g[7]+g[8] && right){
            g[0]*=mainv;
            g[5]*=sharpen;
            g[6]*=sharpen;
            g[7]*=sharpen;
            g[8]*=sharpen;
            float sum = g[0]+g[1]+g[2]+g[3]+g[4]+g[5]+g[6]+g[7]+g[8];
            Output += (texelFetch(RawBuffer, (xy+ivec2(0, 0)), 0).x)*g[0];
            Output += (texelFetch(RawBuffer, (xy+ivec2(-2, 0)), 0).x)*g[1];
            Output += (texelFetch(RawBuffer, (xy+ivec2(0, -2)), 0).x)*g[2];
            Output += (texelFetch(RawBuffer, (xy+ivec2(2, 0)), 0).x)*g[3];
            Output += (texelFetch(RawBuffer, (xy+ivec2(0, 2)), 0).x)*g[4];

            Output += (texelFetch(RawBuffer, (xy+ivec2(-2, -2)), 0).x)*g[5];
            Output += (texelFetch(RawBuffer, (xy+ivec2(2, 2)), 0).x)*g[6];
            Output += (texelFetch(RawBuffer, (xy+ivec2(-2, 2)), 0).x)*g[7];
            Output += (texelFetch(RawBuffer, (xy+ivec2(2, -2)), 0).x)*g[8];
            Output/=sum;
        } else {
            Output = (texelFetch(RawBuffer, (xy), 0).x);
        }
    } else {
        Output = (texelFetch(RawBuffer, (xy), 0).x);
    }


}
