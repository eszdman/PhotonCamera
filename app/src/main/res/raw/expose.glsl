#version 300 es
precision mediump sampler2D;
precision highp float;
uniform sampler2D InputBuffer;
uniform float factor;
uniform vec3 neutralPoint;
out vec3 result;
uniform int yOffset;
float gammaEncode(float x) {
    if(x>1.0) return x;
    return (x <= 0.0031308) ? x * 12.92 : 1.055 * pow(float(x), (1.f/2.4)) - 0.055;
}
void main() {
    ivec2 xyCenter = ivec2(gl_FragCoord.xy);
    xyCenter+=ivec2(0,yOffset);
    /*
    vec3 XYZ = texelFetch(InputBuffer, xyCenter, 0).xyz;
    vec3 xyY = XYZtoxyY(XYZ);
    xyY.z *= factor;
    //xyY.z = min(xyY.z, 1.f);

    xyY.z = sigmoid(xyY.z, 0.9f);
    result = xyYtoXYZ(xyY);*/
    vec3 inp = texelFetch(InputBuffer, xyCenter, 0).xyz*factor;
    /*if(factor < 1.0)
    win.a = win.a*factor*(clamp((1.0-win.a),0.0,0.05)*(1.0/0.05));
    else {
        win.a = win.a*factor*(clamp((1.0-win.a),0.0,0.05)*(1.0/0.05));
    }*/
    //br = br*factor*(clamp((1.0-br),0.0,0.05)*(1.0/0.05));
    //
    result = clamp(inp,vec3(0.0001),neutralPoint);
    result/=neutralPoint;
    float br = (result.r+result.g+result.b)/3.0;
    result/=br;
    br = clamp(br,0.0,1.0);
    br = gammaEncode(br);
    //br = clamp(br,0.0,1.0);
    result*=br;
}
