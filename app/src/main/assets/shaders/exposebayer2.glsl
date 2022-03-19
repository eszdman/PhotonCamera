
precision mediump sampler2D;
precision highp float;
uniform sampler2D InputBuffer;
uniform sampler2D InterpolatedCurve;
uniform float factor;
out vec4 result;
#define NEUTRALPOINT 1.0,1.0,1.0
#define DH (0.0)
#define luminocity(x) dot(x.rgb, vec3(0.299, 0.587, 0.114))
#define CURVE 0
#define INVERSE 0
#define STRLOW 1.0
#define STRHIGH 1.0
float gammaEncode(float x) {
    return sqrt(x);
}
vec4 reinhard_extended(vec4 v, float max_white)
{
    vec4 numerator = v * (vec4(1.0f) + (v / vec4(max_white * max_white)));
    return numerator / (vec4(1.0f) + v);
}
float stddev(vec3 XYZ) {
    float avg = (XYZ.r + XYZ.g + XYZ.b) / 3.;
    vec3 diff = XYZ - avg;
    diff *= diff;
    return sqrt((diff.r + diff.g + diff.b) / 3.);
}
vec3 brIn(vec4 inp, float factor2){
    float br2 = inp.r+inp.g+inp.b+inp.a;
    br2/=4.0;
    #if CURVE == 1
    float texinput = texture(InterpolatedCurve,vec2(br2,0.5)).r;
    #if INVERSE == 1
    texinput = clamp(1.0-texinput,0.0,1.0);
    #endif
    factor2=mix(1.0,factor2,texinput);
    #endif
    inp=clamp(reinhard_extended(inp*factor2,min(factor2,1.0)),0.0,1.0);
    return vec3(inp.r,(inp.g+inp.b)/2.0,inp.a);
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

    vec3 v3 = brIn(inp,STRLOW);
    float br = luminocity(v3);
    br = gammaEncode(clamp(br-DH,0.0,1.0));
    result.r = br;
    result.g = stddev(v3);

    v3 = brIn(inp,STRHIGH);
    br = luminocity(v3);
    br = gammaEncode(clamp(br-DH,0.0,1.0));
    result.b = br;
    result.a = stddev(v3);
}
