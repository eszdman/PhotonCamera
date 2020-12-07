#version 300 es
uniform sampler2D RawBuffer;
uniform sampler2D GreenBuffer;
uniform int WhiteLevel;
uniform int yOffset;
uniform int CfaPattern;
uniform vec3 whitePoint;

uniform sampler2D GainMap;
uniform vec4 blackLevel;
uniform ivec2 RawSize;

//#define greenmin (0.04)
#define greenmin (0.01)
//#define greenmax (0.9)
#define greenmax (0.9)

#define colmin (0.01)
vec4 cubic(float x)
{
    float x2 = x * x;
    float x3 = x2 * x;
    vec4 w;
    w.x =   -x3 + 3.0*x2 - 3.0*x + 1.0;
    w.y =  3.0*x3 - 6.0*x2       + 4.0;
    w.z = -3.0*x3 + 3.0*x2 + 3.0*x + 1.0;
    w.w =  x3;
    return w / 6.0;
}
vec4 textureBicubicHardware(sampler2D sampler, vec2 texCoords){
    // Linear sampling:
    // Software bicubic sampling with hardware acceleration
    vec2 texSize = vec2(textureSize(sampler, 0));
    vec2 invTexSize = 1.0 / texSize;
    texCoords = texCoords * texSize - 0.5;
    vec2 fxy = fract(texCoords);
    texCoords -= fxy;
    vec4 xcubic = cubic(fxy.x);
    vec4 ycubic = cubic(fxy.y);
    vec4 c = texCoords.xxyy + vec2 (-0.5, +1.5).xyxy;
    vec4 s = vec4(xcubic.xz + xcubic.yw, ycubic.xz + ycubic.yw);
    vec4 offset = c + vec4 (xcubic.yw, ycubic.yw) / s;
    offset *= invTexSize.xxyy;
    vec4 sample0 = texture(sampler, offset.xz);
    vec4 sample1 = texture(sampler, offset.yz);
    vec4 sample2 = texture(sampler, offset.xw);
    vec4 sample3 = texture(sampler, offset.yw);
    float sx = s.x / (s.x + s.y);
    float sy = s.z / (s.z + s.w);
    return mix(
    mix(sample3, sample2, sx), mix(sample1, sample0, sx)
    , sy);
}
out vec3 Output;
float interpolateColory(in ivec2 coords){
    ivec2 shift = coords.xy%2;
    bool usegreen = true;
    float green[3];
    green[0] = float(texelFetch(GreenBuffer, (coords+ivec2(0,-1-shift.y)), 0).x);
    green[1] = float(texelFetch(GreenBuffer, (coords+ivec2(0,0)),  0).x);
    green[2] = float(texelFetch(GreenBuffer, (coords+ivec2(0,2-shift.y)),  0).x);
    for(int i = 0; i<3; i++)if(green[i] < greenmin || green[i] > greenmax) usegreen = false;
    if(usegreen){
        float coeff[2];
        coeff[0] = float(texelFetch(RawBuffer, (coords+ivec2(0,-1-shift.y)), 0).x)/(green[0]);
        coeff[1] = float(texelFetch(RawBuffer, (coords+ivec2(0,2-shift.y)),  0).x)/(green[2]);
        return (green[1]*(coeff[0]+coeff[1])/2.);
    } else {
        return ((float(texelFetch(RawBuffer, (coords+ivec2(0,-1)), 0).x)+float(texelFetch(RawBuffer, (coords+ivec2(0,1)), 0).x))/(2.));
    }
}
float interpolateColorx(in ivec2 coords){
    ivec2 shift = coords.xy%2;
    bool usegreen = true;
    float green[3];
    green[0] = float(texelFetch(GreenBuffer, (coords+ivec2(-1-shift.x,0)), 0).x);
    green[1] = float(texelFetch(GreenBuffer, (coords+ivec2(0,0)),  0).x);
    green[2] = float(texelFetch(GreenBuffer, (coords+ivec2(2-shift.x,0)),  0).x);
    for(int i = 0; i<3; i++)if(green[i] < greenmin || green[i] > greenmax) usegreen = false;
    if(usegreen){
        float coeff[2];
        coeff[0] = float(texelFetch(RawBuffer, (coords+ivec2(-1-shift.x,0)), 0).x)/(green[0]);
        coeff[1] = float(texelFetch(RawBuffer, (coords+ivec2(2-shift.x,0)),  0).x)/(green[2]);
        return (green[1]*(coeff[0]+coeff[1])/2.);
    } else {
        return ((float(texelFetch(RawBuffer, (coords+ivec2(-1,0)), 0).x)+float(texelFetch(RawBuffer, (coords+ivec2(1,0)), 0).x))/(2.));
    }
}
float interpolateColor(in ivec2 coords){
    ivec2 shift = coords.xy%2;
    bool usegreen = true;
    float green[5];
    green[0] = float(texelFetch(GreenBuffer, (coords+ivec2(-1,-1)-shift), 0).x);
    green[1] = float(texelFetch(GreenBuffer, (coords+ivec2(2,-1)-shift),  0).x);
    green[2] = float(texelFetch(GreenBuffer, (coords+ivec2(-1,2)-shift),  0).x);
    green[3] = float(texelFetch(GreenBuffer, (coords+ivec2(2,2)-shift),   0).x);
    green[4] = float(texelFetch(GreenBuffer, (coords),              0).x);
    for(int i = 0; i<5; i++)if(green[i] < greenmin || green[i] > greenmax) usegreen = false;
    if(usegreen){
        float coeff[4];
        coeff[0] = float(1.0)*float(texelFetch(RawBuffer, (coords+ivec2(-1,-1)-shift), 0).x)/(green[0]);
        coeff[1] = float(1.0)*float(texelFetch(RawBuffer, (coords+ivec2(2,-1)-shift),  0).x)/(green[1]);
        coeff[2] = float(1.0)*float(texelFetch(RawBuffer, (coords+ivec2(-1,2)-shift),  0).x)/(green[2]);
        coeff[3] = float(1.0)*float(texelFetch(RawBuffer, (coords+ivec2(2,2)-shift),   0).x)/(green[3]);

        //return (green[4]*(avr)/4.);
        return (green[4]*(coeff[0]+coeff[1]+coeff[2]+coeff[3])/4.);
    } else {
        return ((float(texelFetch(RawBuffer, (coords+ivec2(-1,-1)), 0).x)+float(texelFetch(RawBuffer, (coords+ivec2(1,-1)), 0).x)
        +float(texelFetch(RawBuffer, (coords+ivec2(-1,1)), 0).x)+float(texelFetch(RawBuffer, (coords+ivec2(1,1)), 0).x))/(4.));
    }
}
void main() {
    ivec2 xy = ivec2(gl_FragCoord.xy);
    int fact1 = (xy.x/2)%2;
    int fact2 = (xy.y/2)%2;
    xy+=ivec2(0,yOffset);
    vec3 outp;
    if(fact1 ==0 && fact2 == 0) {//rggb
        outp.g = texelFetch(GreenBuffer, (xy), 0).x;
        outp.r = float(texelFetch(RawBuffer, (xy), 0).x);
        outp.b = interpolateColor(xy);
    } else
    if(fact1 ==1 && fact2 == 0) {//grbg
        outp.g = texelFetch(GreenBuffer, (xy), 0).x;
        //outp.r =((float(texelFetch(RawBuffer, (xy+ivec2(-1,0)), 0).x)+float(texelFetch(RawBuffer, (xy+ivec2(1,0)), 0).x))/(2.*float(WhiteLevel)));
        outp.r = interpolateColorx(xy);
        //outp.b =((float(texelFetch(RawBuffer, (xy+ivec2(0,-1)), 0).x)+float(texelFetch(RawBuffer, (xy+ivec2(0,1)), 0).x))/(2.*float(WhiteLevel)));
        outp.b = interpolateColory(xy);

    } else
    if(fact1 ==0 && fact2 == 1) {//gbrg
        outp.g = texelFetch(GreenBuffer, (xy), 0).x;
        //outp.b =((float(texelFetch(RawBuffer, (xy+ivec2(-1,0)), 0).x)+float(texelFetch(RawBuffer, (xy+ivec2(1,0)), 0).x))/(2.*float(WhiteLevel)));
        outp.b = interpolateColorx(xy);
        //outp.r =((float(texelFetch(RawBuffer, (xy+ivec2(0,-1)), 0).x)+float(texelFetch(RawBuffer, (xy+ivec2(0,1)), 0).x))/(2.*float(WhiteLevel)));
        outp.r = interpolateColory(xy);

    } else  {//bggr
        outp.g = texelFetch(GreenBuffer, (xy), 0).x;
        outp.b = float(texelFetch(RawBuffer, (xy), 0).x);
        outp.r = interpolateColor(xy);
    }
    Output = outp;
    vec4 gains = textureBicubicHardware(GainMap, vec2(xy)/vec2(RawSize));
    //Output*=whitePoint;
    Output.r = gains.r*(Output.r-blackLevel.r);
    Output.g = ((gains.g+gains.b)/2.)*(Output.g-(blackLevel.g+blackLevel.b)/2.);
    Output.b = gains.a*(Output.b-blackLevel.a);
    Output/=(1.0-blackLevel.g);

    Output = clamp(Output,0.0,1.0);
}
