#version 300 es
precision highp float;
precision highp sampler2D;
precision highp isampler2D;
precision highp usampler2D;
uniform sampler2D OutputBuffer;

uniform sampler2D InputBuffer;
uniform sampler2D InputBuffer22;
uniform sampler2D MainBuffer;


uniform sampler2D SumWeights;
uniform sampler2D Weight;
uniform isampler2D AlignVectors;
uniform int yOffset;
uniform float alignk;
uniform uvec2 rawsize;
uniform uvec2 alignsize;
uniform uvec2 weightsize;
uniform int CfaPattern;
uniform int number;
uniform float rotation;
out float Output;
#define MIN_NOISE 0.1f
#define MAX_NOISE 1.0f
#define TILESIZE (48)
#define MPY (1.0)
#define MIN (1.0)
#define WP 1.0, 1.0, 1.0
#define BAYER 0
#define ROTATION 0.0
#define HDR 0
#import interpolation
#import coords
void main() {
    ivec2 xy = ivec2(gl_FragCoord.xy);
    xy+=ivec2(0,yOffset);
    ivec2 state = xy%2;
    vec2 xy2 = vec2(xy-state)-vec2(rawsize)/2.0;
    if(abs(ROTATION) >= 0.01){
        xy.x = int(xy2.x*cos(ROTATION)-xy2.y*sin(ROTATION));
        xy.y = int(xy2.y*sin(ROTATION)+xy2.y*cos(ROTATION));
        xy+=ivec2(rawsize)/2;
        xy-=xy%2;
        xy+=state;
    }
    //ivec2 align = ivec2(texelFetch(AlignVectors, (xy/TILESIZE), 0).xy);
    //vec2 xyInterp = vec2(xy)/float(TILESIZE);
    //xyInterp/=vec2(alignsize);
    //xyInterp = vec2(xy)/float(TILESIZE);

    //xyInterp/=vec2(weightsize);
    //float weight = float(texture(SpatialWeights, xyInterp).x);
    //float inp = boxdown22(aligned/2,InputBuffer22)/4.0;
    //float target = boxdown22(xy/2,MainBuffer22)/4.0;
    //float weight = abs(inp-target);
    //float weight = smoothstep(MIN_NOISE, MAX_NOISE, abs(inp-target));
    //float weight = float(texelFetch(SpatialWeights, ivec2(xyInterp), 0).x);

    //alignf/=float(TILESIZE*2);

    //float dist2 = alignf.x*alignf.x+alignf.y*alignf.y;
    //float dist2 = smoothstep(0.0,2.0,abs(alignf.x)+abs(alignf.y));
    //float windoww = 1.0 - (weight)*5.6 - 0.4;

    float windoww = 1.0;
    windoww = texelFetch(Weight, (xy/(TILESIZE*2)), 0).r;
    //windoww = clamp(windoww,0.00,1.0);

    //windoww = float(int(windoww*100.0))/100.0;
    //float outp = (((texelFetch(InputBuffer, aligned, 0).x))*float((windoww))+((texelFetch(OutputBuffer, (xy), 0).x))*float((1.0-windoww)));
    ivec2 outsize = ivec2(textureSize(OutputBuffer, 0));

    ivec2 alignvecSize = ivec2(textureSize(AlignVectors, 0));
    ivec2 shiftAl = ivec2(0,0);
    ivec2 align = ivec2(texelFetch(AlignVectors, mirrorCoords2(xy/(TILESIZE*2)+shiftAl,alignvecSize), 0).rg);
    align = mirrorCoords2((xy/2)+align,ivec2((outsize-1)/2))*2 + state;
    float sumweights = texelFetch(SumWeights, mirrorCoords2(xy/(TILESIZE*2)+shiftAl,alignvecSize), 0).r;
    windoww = texelFetch(Weight, mirrorCoords2(xy/(TILESIZE*2)+shiftAl,alignvecSize), 0).r/sumweights;
    windoww = 1.0-clamp((windoww-1.0/4.7)*15.0,0.0,1.0);


    //if(number == 1){
    //    Output = (texelFetch(OutputBuffer, xy, 0).x)/float(number)+((texelFetch(InputBuffer, (align), 0).x))*windoww;
    //} else
    //Output = (texelFetch(OutputBuffer, xy, 0).x)/sumweights+((texelFetch(InputBuffer, (align), 0).x))*windoww/sumweights;
    #if HDR == 1

    vec3 point = clamp(vec3(
    (texelFetch(InputBuffer,  (align/2)*2+ivec2(0,0)+ivec2(BAYER%2,BAYER/2), 0).x),
    ((texelFetch(InputBuffer, (align/2)*2+ivec2(0,1)+ivec2(BAYER%2,BAYER/2), 0).x)+
     (texelFetch(InputBuffer, (align/2)*2+ivec2(1,0)+ivec2(BAYER%2,BAYER/2), 0).x))/2.0,
    (texelFetch(InputBuffer,  (align/2)*2+ivec2(1,1)+ivec2(BAYER%2,BAYER/2), 0).x))/MPY,vec3(0.0),vec3(WP).rgb)/vec3(WP);
    float bright = (point.r+point.g+point.b)/(3.0);
    if(MPY < 1.0) windoww *= clamp(1.0-bright,0.0,0.2)/0.2;
    if(MPY/MIN > 1.0) windoww *= clamp(bright,0.0,0.2)/0.2;


    #endif
    float br1 = ((texelFetch(OutputBuffer, xy, 0).x));
    float br2 = ((texelFetch(InputBuffer, (align), 0).x));
    Output = mix(br1,br2, windoww/float(number));
}
