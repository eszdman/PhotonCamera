#version 300 es
precision mediump sampler2D;
precision highp float;
uniform sampler2D InputBuffer;
uniform float factor;
uniform vec3 neutralPoint;
out vec4 result;
uniform int yOffset;
#define DR (1.4)
#define DH (0.0)
#define EXPOSESHIFT (0.7)
#define EXPOSEMPY (1.3)
#define EXPOSEMIN (0.5)
#define EXPOSEMAX (1.0)
/*float gammaEncode(float x) {
    if(x>1.0) return x;
    return (x <= 0.0031308) ? x * 12.92 : 1.055 * pow(float(x), (1.f/DR));
}*/
float gammaEncode(float x) {
    if(x>1.0) return x;
    return pow(float(x), (1.f/DR));
}
void main() {
    ivec2 xyCenter = ivec2(gl_FragCoord.xy);
    xyCenter+=ivec2(0,yOffset);
    xyCenter*=2;
    /*
    vec3 XYZ = texelFetch(InputBuffer, xyCenter, 0).xyz;
    vec3 xyY = XYZtoxyY(XYZ);
    xyY.z *= factor;
    //xyY.z = min(xyY.z, 1.f);

    xyY.z = sigmoid(xyY.z, 0.9f);
    result = xyYtoXYZ(xyY);*/
    vec4 inp;
    inp.r = texelFetch(InputBuffer, xyCenter, 0).r;
    inp.g = texelFetch(InputBuffer, xyCenter+ivec2(1,0), 0).r;
    inp.b = texelFetch(InputBuffer, xyCenter+ivec2(0,1), 0).r;
    inp.a = texelFetch(InputBuffer, xyCenter+ivec2(1,1), 0).r;
    if(factor > 1.0){
        float br2 = inp.r+inp.g+inp.b+inp.a;
        br2/=4.0;
        inp*=factor*clamp((EXPOSESHIFT-br2)*EXPOSEMPY,EXPOSEMIN,EXPOSEMAX);
    }
    /*if(factor < 1.0)
    win.a = win.a*factor*(clamp((1.0-win.a),0.0,0.05)*(1.0/0.05));
    else {
        win.a = win.a*factor*(clamp((1.0-win.a),0.0,0.05)*(1.0/0.05));
    }*/
    //br = br*factor*(clamp((1.0-br),0.0,0.05)*(1.0/0.05));
    //
    result = clamp(inp,vec4(0.0001),neutralPoint.rggb);
    result/=neutralPoint.rggb;
    float br = (result.r+result.g+result.b+result.a)/4.0;
    result/=br;
    /*if(br > 0.93 && factor <= 1.1){
        result = mix(vec3((result.r+result.g+result.b)/3.0),result,(1.0-br)/0.03);
    }*/
    br = gammaEncode(clamp(br-DH,0.0,1.0));

    //br = clamp(br,0.0,1.0);
    result*=br;
}
