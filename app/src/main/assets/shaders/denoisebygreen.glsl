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
#define EPS (0.001)
#import median
void main() {
    ivec2 xy = ivec2(gl_FragCoord.xy);
    int fact1 = xy.x%2;
    int fact2 = xy.y%2;
    if(fact1+fact2 != 1){
        vec2 vin[5];
        vin[0].g = (texelFetch(GreenBuffer, (xy+ivec2(0,0)), 0).x)+EPS;
        vin[1].g = (texelFetch(GreenBuffer, (xy+ivec2(-2,0)), 0).x)+EPS;
        vin[2].g = (texelFetch(GreenBuffer, (xy+ivec2(0,-2)), 0).x)+EPS;
        vin[3].g = (texelFetch(GreenBuffer, (xy+ivec2(2,0)), 0).x)+EPS;
        vin[4].g = (texelFetch(GreenBuffer, (xy+ivec2(0,2)), 0).x)+EPS;

        /*vin[5].g = (texelFetch(GreenBuffer, (xy+ivec2(-2,-2)), 0).x);
        vin[6].g = (texelFetch(GreenBuffer, (xy+ivec2(2,-2)), 0).x);
        vin[7].g = (texelFetch(GreenBuffer, (xy+ivec2(2,2)), 0).x);
        vin[8].g = (texelFetch(GreenBuffer, (xy+ivec2(-2,2)), 0).x);*/



        vin[0].r = (texelFetch(RawBuffer, (xy+ivec2(0, 0)), 0).x)*vin[0].g;
        vin[1].r = (texelFetch(RawBuffer, (xy+ivec2(-2, 0)), 0).x)*vin[1].g;
        vin[2].r = (texelFetch(RawBuffer, (xy+ivec2(0, -2)), 0).x)*vin[2].g;
        vin[3].r = (texelFetch(RawBuffer, (xy+ivec2(2, 0)), 0).x)*vin[3].g;
        vin[4].r = (texelFetch(RawBuffer, (xy+ivec2(0, 2)), 0).x)*vin[4].g;

        /*vin[5].r = (texelFetch(RawBuffer, (xy+ivec2(-2,-2)), 0).x)*vin[5].g;
        vin[6].r = (texelFetch(RawBuffer, (xy+ivec2(2, -2)), 0).x)*vin[6].g;
        vin[7].r = (texelFetch(RawBuffer, (xy+ivec2(2, 2)), 0).x)*vin[7].g;
        vin[8].r = (texelFetch(RawBuffer, (xy+ivec2(-2, 2)), 0).x)*vin[8].g;*/

        vec2 outp = median5(vin);
        Output = outp.r/outp.g;
    } else {
        Output = (texelFetch(RawBuffer, (xy), 0).x);
    }


}
