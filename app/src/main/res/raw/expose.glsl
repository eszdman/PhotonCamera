#version 300 es
precision mediump sampler2D;
precision highp float;
uniform sampler2D InputBuffer;
uniform float factor;
uniform vec3 neutralPoint;
out vec3 result;
uniform int yOffset;
#define DR (1.4)
#define DH (0.0)
//#import gaussian
/*float gammaEncode(float x) {
    if(x>1.0) return x;
    return (x <= 0.0031308) ? x * 12.92 : 1.055 * pow(float(x), (1.f/DR));
}*/
float gammaEncode(float x) {
    if(x>1.0) return x;
    return pow(float(x), (1.f/DR));
}
float gammaEncode2(float x) {
    if(x>1.0) return x;
    return pow(float(x), (1.f/(DR*factor)));
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
    vec3 inp = texelFetch(InputBuffer, xyCenter, 0).xyz;
    float finalF = factor;
    if(factor > 1.0){
        float br2 = inp.r+inp.g+inp.b;
        br2/=3.0;
        //inp*=mix(factor,1.0,clamp((br2-0.5),0.0,0.5)*2.0);
        br2 = 1.0-clamp(abs(br2-0.5)*2.0,0.85,1.0);
        br2/=0.15;
        br2*=br2;
        inp*=mix(1.0,factor,br2);
        //finalF = unscaledGaussian(br2,3.0)*factor;
    }
    //inp*=finalF;
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

    /*if(br > 0.93 && factor <= 1.1){
        result = mix(vec3((result.r+result.g+result.b)/3.0),result,(1.0-br)/0.03);
    }*/
    //result.r = gammaEncode(clamp(result.r-DH,0.0,1.0));
    //result.g = gammaEncode(clamp(result.g-DH,0.0,1.0));
    //result.b = gammaEncode(clamp(result.b-DH,0.0,1.0));

    br = gammaEncode(clamp(br-DH,0.0,1.0));

    //br = clamp(br,0.0,1.0);



    result*=br;

}
