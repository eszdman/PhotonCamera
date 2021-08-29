#version 300 es
precision highp float;
precision mediump sampler2D;
uniform sampler2D RawBuffer;
uniform sampler2D GreenBuffer;
uniform int yOffset;
uniform int CfaPattern;


//#define greenmin (0.04)
#define greenmin (0.01)
//#define greenmax (0.9)
#define greenmax (0.99)
#import interpolation
out vec3 Output;
float interpolateColor(in ivec2 coords, in ivec2 shift){
    bool usegreen = true;
    float green[5];
    ivec2 cent = ivec2((shift.x*2 - 1)*2 -1,(shift.y*2 - 1)*2 -1);
    green[0] = float(texelFetch(GreenBuffer, (coords+ivec2(-1,-1)+cent), 0).x);
    green[1] = float(texelFetch(GreenBuffer, (coords+ivec2(1,-1)+cent),  0).x);
    green[2] = float(texelFetch(GreenBuffer, (coords+ivec2(-1,1)+cent),  0).x);
    green[3] = float(texelFetch(GreenBuffer, (coords+ivec2(1,1)+cent),   0).x);
    green[4] = float(texelFetch(GreenBuffer, (coords),              0).x);
    for(int i = 0; i<5; i++)if(green[i] < greenmin || green[i] > greenmax) usegreen = false;
    if(usegreen){
        float coeff[4];
        coeff[0] = float(texelFetch(RawBuffer, (coords+ivec2(-1,-1)+cent), 0).x)/(green[0]);
        coeff[1] = float(texelFetch(RawBuffer, (coords+ivec2(1,-1)+cent),  0).x)/(green[1]);
        coeff[2] = float(texelFetch(RawBuffer, (coords+ivec2(-1,1)+cent),  0).x)/(green[2]);
        coeff[3] = float(texelFetch(RawBuffer, (coords+ivec2(1,1)+cent),   0).x)/(green[3]);
        return (green[4]*(coeff[0]+coeff[1]+coeff[2]+coeff[3])/4.);
    } else {
        return ((float(texelFetch(RawBuffer, (coords+ivec2(-1,-1)), 0).x)+float(texelFetch(RawBuffer, (coords+ivec2(1,-1)), 0).x)
        +float(texelFetch(RawBuffer, (coords+ivec2(-1,1)), 0).x)+float(texelFetch(RawBuffer, (coords+ivec2(1,1)), 0).x))/(4.));
        }
}
float interpolateColorx(in ivec2 coords, in ivec2 shift){
    bool usegreen = true;
    float green[3];
    ivec2 cent = ivec2(-1-shift.x,-1-shift.y);
    green[0] = float(texelFetch(GreenBuffer, (coords+ivec2(cent.x,0)), 0).x);
    green[1] = float(texelFetch(GreenBuffer, (coords+ivec2(0,0)),  0).x);
    green[2] = float(texelFetch(GreenBuffer, (coords+ivec2(cent.x+3,0)),  0).x);
    for(int i = 0; i<3; i++)if(green[i] < greenmin || green[i] > greenmax) usegreen = false;
    if(usegreen){
        float coeff[2];
        coeff[0] = float(texelFetch(RawBuffer, (coords+ivec2(cent.x,0)), 0).x)/(green[0]);
        coeff[1] = float(texelFetch(RawBuffer, (coords+ivec2(cent.x+3,0)),  0).x)/(green[2]);
        return (green[1]*(coeff[0]+coeff[1])/2.);
    } else {
        return ((float(texelFetch(RawBuffer, (coords+ivec2(cent.x,0)), 0).x)+float(texelFetch(RawBuffer, (coords+ivec2(cent.x+3,0)), 0).x))/(2.));
    }
}
float interpolateColory(in ivec2 coords, in ivec2 shift){
    bool usegreen = true;
    float green[3];
    ivec2 cent = ivec2(-1-shift.x,-1-shift.y);
    green[0] = float(texelFetch(GreenBuffer, (coords+ivec2(0,cent.y)), 0).x);
    green[1] = float(texelFetch(GreenBuffer, (coords+ivec2(0,0)),  0).x);
    green[2] = float(texelFetch(GreenBuffer, (coords+ivec2(0,cent.y+3)),  0).x);
    for(int i = 0; i<3; i++)if(green[i] < greenmin || green[i] > greenmax) usegreen = false;
    if(usegreen){
        float coeff[2];
        coeff[0] = float(texelFetch(RawBuffer, (coords+ivec2(0,cent.y)), 0).x)/(green[0]);
        coeff[1] = float(texelFetch(RawBuffer, (coords+ivec2(0,cent.y+3)),  0).x)/(green[2]);
        return (green[1]*(coeff[0]+coeff[1])/2.);
    } else {
        return ((float(texelFetch(RawBuffer, (coords+ivec2(0,cent.y)), 0).x)+float(texelFetch(RawBuffer, (coords+ivec2(0,cent.y+3)), 0).x))/(2.));
    }
}
void main() {
    ivec2 xy = ivec2(gl_FragCoord.xy);
    int fact1 = (xy.x/2)%2;
    int fact2 = (xy.y/2)%2;
    ivec2 shift = xy%2;
    xy+=ivec2(0,yOffset);
    if(fact1 ==0 && fact2 == 0) {//rggb
        Output.g = texelFetch(GreenBuffer, (xy), 0).x;
        Output.r = float(texelFetch(RawBuffer, (xy), 0).x);
        Output.b = interpolateColor(xy,shift);
    } else
    if(fact1 ==1 && fact2 == 0) {//grbg
        Output.g = texelFetch(GreenBuffer, (xy), 0).x;
        Output.r = interpolateColorx(xy,shift);
        Output.b = interpolateColory(xy,shift);

    } else
    if(fact1 ==0 && fact2 == 1) {//gbrg
        Output.g = texelFetch(GreenBuffer, (xy), 0).x;
        Output.b = interpolateColorx(xy,shift);
        Output.r = interpolateColory(xy,shift);

    } else  {//bggr
        Output.g = texelFetch(GreenBuffer, (xy), 0).x;
        Output.b = float(texelFetch(RawBuffer, (xy), 0).x);
        Output.r = interpolateColor(xy,shift);
    }
    //Output.rb = vec2(Output.g);
    Output = clamp(Output,0.0,1.0);
}