#version 300 es
precision mediump float;
precision mediump usampler2D;
uniform usampler2D RawBuffer;
uniform usampler2D GreenBuffer;
uniform int yOffset;
uniform int CfaPattern;

#define greenmin (0.15)
out uvec4 Output;
uint interpolateColor(ivec2 coords){
    bool usegreen = true;
    float green[4];
    green[0] = float(GreenBuffer(RawBuffer, (coords+ivec2(-1,-1)), 0).x);
    green[1] = float(GreenBuffer(RawBuffer, (coords+ivec2(1,-1)),  0).x);
    green[2] = float(GreenBuffer(RawBuffer, (coords+ivec2(-1,1)),  0).x);
    green[3] = float(GreenBuffer(RawBuffer, (coords+ivec2(1,1)),   0).x);
    for(int i = 0; i<4; i++)if(green[i] < greenmin) usegreen = false;
    if(usegreen){
        float coeff[4];
        coeff[0] = float(texelFetch(RawBuffer, (coords+ivec2(-1,-1)), 0).x)/green[0];
        coeff[1] = float(texelFetch(RawBuffer, (coords+ivec2(1,-1)),  0).x)/green[1];
        coeff[2] = float(texelFetch(RawBuffer, (coords+ivec2(-1,1)),  0).x)/green[2];
        coeff[3] = float(texelFetch(RawBuffer, (coords+ivec2(1,1)),   0).x)/green[3];
        float greencenter = float(texelFetch(RawBuffer, (coords), 0).x);
        return uint(greencenter*(coeff[0]+coeff[1]+coeff[2]+coeff[3])/4.);
    } else {
        return uint((float(texelFetch(RawBuffer, (coords+ivec2(-1,-1)), 0).x)+float(texelFetch(RawBuffer, (coords+ivec2(1,-1)), 0).x)
        +float(texelFetch(RawBuffer, (coords+ivec2(-1,1)), 0).x)+float(texelFetch(RawBuffer, (coords+ivec2(1,1)), 0).x))/4.);
    }
    return 0;
}
void main() {
    ivec2 xy = ivec2(gl_FragCoord.xy);
    xy+=ivec2(CfaPattern%2,yOffset+CfaPattern/2);
    int fact1 = coords.x%2;
    int fact2 = coords.y%2;
    uvec4 outp;
    if(fact1 ==0 && fact2 == 0) {//grbg
        outp.g = texelFetch(GreenBuffer, (xy), 0).x;
        outp.r =uint((float(texelFetch(RawBuffer, (xy+ivec2(-1,0)), 0).x)+float(texelFetch(RawBuffer, (xy+ivec2(1,0)), 0).x))/2.);
        outp.b =uint((float(texelFetch(RawBuffer, (xy+ivec2(0,-1)), 0).x)+float(texelFetch(RawBuffer, (xy+ivec2(0,1)), 0).x))/2.);
    } else
    if(fact1 ==1 && fact2 == 0) {//bggr
        outp.g = texelFetch(GreenBuffer, (xy), 0).x;
        outp.b = texelFetch(RawBuffer, (xy), 0).x;
        outp.r = interpolateColor(xy);
    } else
    if(fact1 ==0 && fact2 == 1) {//rggb
        outp.g = texelFetch(GreenBuffer, (xy), 0).x;
        outp.r = texelFetch(RawBuffer, (xy), 0).x;
        outp.b = interpolateColor(xy);
    } else  {//gbrg
        outp.g = texelFetch(GreenBuffer, (xy), 0).x;
        outp.b =uint((float(texelFetch(RawBuffer, (xy+ivec2(-1,0)), 0).x)+float(texelFetch(RawBuffer, (xy+ivec2(1,0)), 0).x))/2.);
        outp.r =uint((float(texelFetch(RawBuffer, (xy+ivec2(0,-1)), 0).x)+float(texelFetch(RawBuffer, (xy+ivec2(0,1)), 0).x))/2.);
    }
    Output = outp;
}