#version 300 es
precision mediump sampler2D;
precision highp float;
uniform sampler2D InputBuffer;
uniform sampler2D InterpolatedCurve;
uniform float factor;
out vec2 result;
#define NEUTRALPOINT 1.0,1.0,1.0
#define DH (0.0)
#define luminocity(x) dot(x.rgb, vec3(0.299, 0.587, 0.114))
#define CURVE 0
#define INVERSE 0
float gammaEncode(float x) {
    return sqrt(x);
}
float stddev(vec3 XYZ) {
    float avg = (XYZ.r + XYZ.g + XYZ.b) / 3.;
    vec3 diff = XYZ - avg;
    diff *= diff;
    return sqrt((diff.r + diff.g + diff.b) / 3.);
}
void main() {
    ivec2 xyCenter = ivec2(gl_FragCoord.xy);
    xyCenter*=2;
    vec4 inp;
    inp.r = texelFetch(InputBuffer, xyCenter, 0).r;
    inp.g = texelFetch(InputBuffer, xyCenter+ivec2(1,0), 0).r;
    inp.b = texelFetch(InputBuffer, xyCenter+ivec2(0,1), 0).r;
    inp.a = texelFetch(InputBuffer, xyCenter+ivec2(1,1), 0).r;
    inp = clamp(inp,vec4(0.0001),vec3(NEUTRALPOINT).rggb)/vec3(NEUTRALPOINT).rggb;
    //if(factor > 1.0){
        float br2 = inp.r+inp.g+inp.b+inp.a;
        br2/=4.0;
        float factor2 = factor;
        //inp*=factor*clamp((0.7-br2)*1.3,0.5,1.0);
    #if CURVE == 1
    float texinput = texture(InterpolatedCurve,vec2(br2,0.5)).r;
    #if INVERSE == 1
    texinput = clamp(1.0-texinput,0.0,1.0);
    #endif
    factor2=mix(1.0,factor2,texinput);
    #endif

    inp=clamp(inp*factor2,0.0,1.0);
    //}
    vec3 v3 = vec3(inp.r,(inp.g+inp.b)/2.0,inp.a);
    float br = luminocity(v3);
    br = gammaEncode(clamp(br-DH,0.0,1.0));
    result.r = br;
    result.g = stddev(v3);
}
