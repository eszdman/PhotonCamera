#version 300 es
precision mediump float;
precision mediump usampler2D;
precision mediump sampler2D;
uniform usampler2D RawBuffer;
uniform sampler2D GreenBuffer;
uniform int WhiteLevel;
uniform int yOffset;
uniform int CfaPattern;

#define greenmin (0.04)
#define greenmax (0.90)
out vec4 Output;

float interpolateColor(ivec2 coords){
    bool usegreen = true;
    float green[5];
    ivec2 shift = ivec2(CfaPattern%2,CfaPattern/2);
    green[0] = float(texelFetch(GreenBuffer, (coords+ivec2(-1,-1)-shift), 0).x);
    green[1] = float(texelFetch(GreenBuffer, (coords+ivec2(1,-1)-shift),  0).x);
    green[2] = float(texelFetch(GreenBuffer, (coords+ivec2(-1,1)-shift),  0).x);
    green[3] = float(texelFetch(GreenBuffer, (coords+ivec2(1,1)-shift),   0).x);
    green[4] = float(texelFetch(GreenBuffer, (coords-shift),              0).x);
    for(int i = 0; i<5; i++)if(green[i] < greenmin) usegreen = false;
    if(usegreen){
        float coeff[4];
        coeff[0] = float(texelFetch(RawBuffer, (coords+ivec2(-1,-1)), 0).x)/(float(WhiteLevel)*green[0]);
        coeff[1] = float(texelFetch(RawBuffer, (coords+ivec2(1,-1)),  0).x)/(float(WhiteLevel)*green[1]);
        coeff[2] = float(texelFetch(RawBuffer, (coords+ivec2(-1,1)),  0).x)/(float(WhiteLevel)*green[2]);
        coeff[3] = float(texelFetch(RawBuffer, (coords+ivec2(1,1)),   0).x)/(float(WhiteLevel)*green[3]);
        return (green[4]*(coeff[0]+coeff[1]+coeff[2]+coeff[3])/4.);
    } else {
        return ((float(texelFetch(RawBuffer, (coords+ivec2(-1,-1)), 0).x)+float(texelFetch(RawBuffer, (coords+ivec2(1,-1)), 0).x)
        +float(texelFetch(RawBuffer, (coords+ivec2(-1,1)), 0).x)+float(texelFetch(RawBuffer, (coords+ivec2(1,1)), 0).x))/(4.*float(WhiteLevel)));
        }
}
float interpolateColorx(ivec2 coords){
    bool usegreen = true;
    float green[3];
    ivec2 shift = ivec2(CfaPattern%2,CfaPattern/2);
    green[0] = float(texelFetch(GreenBuffer, (coords+ivec2(-1,0)-shift), 0).x);
    green[1] = float(texelFetch(GreenBuffer, (coords+ivec2(0,0)-shift),  0).x);
    green[2] = float(texelFetch(GreenBuffer, (coords+ivec2(1,0)-shift),  0).x);
    for(int i = 0; i<3; i++)if(green[i] < greenmin) usegreen = false;
    if(usegreen){
        float coeff[2];
        coeff[0] = float(texelFetch(RawBuffer, (coords+ivec2(-1,0)), 0).x)/(float(WhiteLevel)*green[0]);
        coeff[1] = float(texelFetch(RawBuffer, (coords+ivec2(1,0)),  0).x)/(float(WhiteLevel)*green[2]);
        return (green[1]*(coeff[0]+coeff[1])/2.);
    } else {
        return ((float(texelFetch(RawBuffer, (coords+ivec2(-1,0)), 0).x)+float(texelFetch(RawBuffer, (coords+ivec2(1,0)), 0).x))/(2.*float(WhiteLevel)));
    }
}
float interpolateColory(ivec2 coords){
    bool usegreen = true;
    float green[3];
    ivec2 shift = ivec2(CfaPattern%2,CfaPattern/2);
    green[0] = float(texelFetch(GreenBuffer, (coords+ivec2(0,-1)-shift), 0).x);
    green[1] = float(texelFetch(GreenBuffer, (coords+ivec2(0,0)-shift),  0).x);
    green[2] = float(texelFetch(GreenBuffer, (coords+ivec2(0,1)-shift),  0).x);
    for(int i = 0; i<3; i++)if(green[i] < greenmin) usegreen = false;
    if(usegreen){
        float coeff[2];
        coeff[0] = float(texelFetch(RawBuffer, (coords+ivec2(0,-1)), 0).x)/(float(WhiteLevel)*green[0]);
        coeff[1] = float(texelFetch(RawBuffer, (coords+ivec2(0,1)),  0).x)/(float(WhiteLevel)*green[2]);
        return (green[1]*(coeff[0]+coeff[1])/2.);
    } else {
        return ((float(texelFetch(RawBuffer, (coords+ivec2(0,-1)), 0).x)+float(texelFetch(RawBuffer, (coords+ivec2(0,1)), 0).x))/(2.*float(WhiteLevel)));
    }
}
void main() {
    ivec2 xy = ivec2(gl_FragCoord.xy);
    int fact1 = xy.x%2;
    int fact2 = xy.y%2;
    ivec2 shift = ivec2(CfaPattern%2,CfaPattern/2);
    xy+=ivec2(CfaPattern%2,yOffset+CfaPattern/2);
    vec4 outp;
    if(fact1 ==0 && fact2 == 0) {//rggb
        outp.g = texelFetch(GreenBuffer, (xy-shift), 0).x;
        outp.r = float(texelFetch(RawBuffer, (xy), 0).x)/float(WhiteLevel);
        outp.b = interpolateColor(xy);
    } else
    if(fact1 ==1 && fact2 == 0) {//grbg
        outp.g = texelFetch(GreenBuffer, (xy-shift), 0).x;
        //outp.r =((float(texelFetch(RawBuffer, (xy+ivec2(-1,0)), 0).x)+float(texelFetch(RawBuffer, (xy+ivec2(1,0)), 0).x))/(2.*float(WhiteLevel)));
        outp.r = interpolateColorx(xy);
        //outp.b =((float(texelFetch(RawBuffer, (xy+ivec2(0,-1)), 0).x)+float(texelFetch(RawBuffer, (xy+ivec2(0,1)), 0).x))/(2.*float(WhiteLevel)));
        outp.b = interpolateColory(xy);

    } else
    if(fact1 ==0 && fact2 == 1) {//gbrg
        outp.g = texelFetch(GreenBuffer, (xy-shift), 0).x;
        //outp.b =((float(texelFetch(RawBuffer, (xy+ivec2(-1,0)), 0).x)+float(texelFetch(RawBuffer, (xy+ivec2(1,0)), 0).x))/(2.*float(WhiteLevel)));
        outp.b = interpolateColorx(xy);
        //outp.r =((float(texelFetch(RawBuffer, (xy+ivec2(0,-1)), 0).x)+float(texelFetch(RawBuffer, (xy+ivec2(0,1)), 0).x))/(2.*float(WhiteLevel)));
        outp.r = interpolateColory(xy);

    } else  {//bggr
        outp.g = texelFetch(GreenBuffer, (xy-shift), 0).x;
        outp.b = float(texelFetch(RawBuffer, (xy), 0).x)/float(WhiteLevel);
        outp.r = interpolateColor(xy);
    }
    Output = outp;
}