#version 300 es
precision mediump float;
precision mediump usampler2D;
precision mediump sampler2D;
uniform sampler2D Fullbuffer;
uniform sampler2D GainMap;
uniform int RawSizeX;
uniform int RawSizeY;
uniform vec4 blackLevel;
uniform vec3 neutralPoint;
uniform float gain;
uniform float saturation;
uniform int yOffset;
//Color mat's
uniform mat3 sensorToIntermediate; // Color transform from XYZ to a wide-gamut colorspace
uniform mat3 intermediateToSRGB; // Color transform from wide-gamut colorspace to sRGB

out vec4 Output;
#define x1 2.8114
#define x2 -3.5701
#define x3 1.6807
//CSEUS Gamma
//1.0 0.86 0.76 0.57 0.48 0.0 0.09 0.3
//0.999134635 0.97580 0.94892548 0.8547916 0.798550103 0.0000000 0.29694557 0.625511972
//#define x1 2.8586f
//#define x2 -3.1643f
//#define x3 1.2899f
float gammaEncode2(float x) {
    return (x <= 0.0031308) ? x * 12.92 : 1.055 * pow(float(x), (1.f/gain)) - 0.055;
}
//Apply Gamma correction
vec3 gammaCorrectPixel(vec3 x) {
    vec3 xx = x*x;
    vec3 xxx = xx*x;
    return (x1*x+x2*xx+x3*xxx);
}

vec3 gammaCorrectPixel2(vec3 rgb) {
    rgb.x = gammaEncode2(rgb.x);
    rgb.y = gammaEncode2(rgb.y);
    rgb.z = gammaEncode2(rgb.z);
    return rgb;
}

/*vec3 tonemap(vec3 rgb) {
    vec3 sorted = clamp(rgb, 0.f, 1.f);
    float tmp;
    int permutation = 0;
    // Sort the RGB channels by value
    if (sorted.z < sorted.y) {
        tmp = sorted.z;
        sorted.z = sorted.y;
        sorted.y = tmp;
        permutation |= 1;
    }
    if (sorted.y < sorted.x) {
        tmp = sorted.y;
        sorted.y = sorted.x;
        sorted.x = tmp;
        permutation |= 2;
    }
    if (sorted.z < sorted.y) {
        tmp = sorted.z;
        sorted.z = sorted.y;
        sorted.y = tmp;
        permutation |= 4;
    }
    vec2 minmax;
    minmax.x = sorted.x;
    minmax.y = sorted.z;
    // Apply tonemapping curve to min, max RGB channel values
    minmax = native_powr(minmax, 3.f) * toneMapCoeffs.x +
    native_powr(minmax, 2.f) * toneMapCoeffs.y +
    minmax * toneMapCoeffs.z + toneMapCoeffs.w;
    // Rescale middle value
    float newMid;
    if (sorted.z == sorted.x) {
        newMid = minmax.y;
    } else {
        newMid = minmax.x + ((minmax.y - minmax.x) * (sorted.y - sorted.x) /
        (sorted.z - sorted.x));
    }
    vec3 finalRGB;
    switch (permutation) {
        case 0: // b >= g >= r
        finalRGB.x = minmax.x;
        finalRGB.y = newMid;
        finalRGB.z = minmax.y;
        break;
        case 1: // g >= b >= r
        finalRGB.x = minmax.x;
        finalRGB.z = newMid;
        finalRGB.y = minmax.y;
        break;
        case 2: // b >= r >= g
        finalRGB.y = minmax.x;
        finalRGB.x = newMid;
        finalRGB.z = minmax.y;
        break;
        case 3: // g >= r >= b
        finalRGB.z = minmax.x;
        finalRGB.x = newMid;
        finalRGB.y = minmax.y;
        break;
        case 6: // r >= b >= g
        finalRGB.y = minmax.x;
        finalRGB.z = newMid;
        finalRGB.x = minmax.y;
        break;
        case 7: // r >= g >= b
        finalRGB.z = minmax.x;
        finalRGB.y = newMid;
        finalRGB.x = minmax.y;
        break;
        case 4: // impossible
        case 5: // impossible
        default:
        finalRGB.x = 0.f;
        finalRGB.y = 0.f;
        finalRGB.z = 0.f;
        break;
    }
    return clamp(finalRGB, 0.f, 1.f);
}*/
vec3 applyColorSpace(vec3 pRGB){
    pRGB.x = clamp(pRGB.x, 0., neutralPoint.x);
    pRGB.y = clamp(pRGB.y, 0., neutralPoint.y);
    pRGB.z = clamp(pRGB.z, 0., neutralPoint.z);
    pRGB.x*=1.08;
    pRGB = sensorToIntermediate*pRGB;
    //pRGB = tonemap(pRGB);
    return gammaCorrectPixel2(gammaCorrectPixel(clamp(intermediateToSRGB*pRGB, 0., 1.)));
}
// Source: https://lolengine.net/blog/2013/07/27/rgb-to-hsv-in-glsl
vec3 rgb2hsv(vec3 c) {
    vec4 K = vec4(0.f, -1.f / 3.f, 2.f / 3.f, -1.f);
    vec4 p = mix(vec4(c.bg, K.wz), vec4(c.gb, K.xy), step(c.b, c.g));
    vec4 q = mix(vec4(p.xyw, c.r), vec4(c.r, p.yzx), step(p.x, c.r));
    float d = q.x - min(q.w, q.y);
    float e = 1.0e-10;
    return vec3(abs(q.z + (q.w - q.y) / (6.f * d + e)), d / (q.x + e), q.x);
}
vec3 hsv2rgb(vec3 c) {
    vec4 K = vec4(1., 2. / 3., 1. / 3., 3.);
    vec3 p = abs(fract(c.xxx + K.xyz) * 6. - K.www);
    return c.z * mix(K.xxx, clamp(p - K.xxx, 0., 1.), c.y);
}

vec3 linearizeAndGainMap(ivec2 coords){
    vec3 pRGB;
    vec4 inbuff = texelFetch(Fullbuffer,coords,0);
    vec2 xyInterp = vec2(float(coords.x) / float(RawSizeX), float(coords.y) / float(RawSizeY));
    vec4 gains = texture(GainMap, xyInterp);
    pRGB.r = gains.r*float(inbuff.r-blackLevel.r);
    pRGB.g = ((gains.g+gains.b)/2.)*float(inbuff.g-(blackLevel.g+blackLevel.b)/2.);
    pRGB.b = gains.a*float(inbuff.b-blackLevel.a);
    return pRGB;
}
vec3 saturate(vec3 rgb) {
   vec3 hsv = rgb2hsv(rgb);
   hsv.g = clamp(hsv.g*(saturation+rgb.g*0.2+rgb.b*0.2),0.,1.);
   rgb = hsv2rgb(hsv);
    return rgb;
}
void main() {
    ivec2 xy = ivec2(gl_FragCoord.xy);
    xy+=ivec2(0,yOffset);
    vec3 pRGB = linearizeAndGainMap(xy);
    vec3 sRGB = applyColorSpace(pRGB);
    sRGB = saturate(sRGB);
    Output = vec4(sRGB.r,sRGB.g,sRGB.b,1.0);
}