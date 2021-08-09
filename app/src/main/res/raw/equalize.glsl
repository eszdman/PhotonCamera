#version 300 es
precision highp sampler2D;
precision highp float;
uniform float Equalize;
uniform sampler2D Histogram;
uniform sampler2D Shadows;
uniform sampler2D Equalizing;
uniform sampler2D LookupTable;
uniform sampler2D InputBuffer;
//uniform vec4 toneMapCoeffs;
out vec3 Output;
//#import interpolation
#define luminocity(x) dot(x.rgb, vec3(0.299, 0.587, 0.114))
#import xyytoxyz
#import xyztoxyy
#define BR (0.5)
#define EPS (0.0008)
#define EPS2 (0.0004)
#define EPSAMP (3.0)
#define BL2 (0.0)
#define LUT 0
uniform vec4 toneMapCoeffs; // Coefficients for a polynomial tonemapping curve
uniform sampler2D TonemapTex;
#define PI (3.1415926535)
#define TONEMAP_GAMMA (1.5)
// Source: https://lolengine.net/blog/2013/07/27/rgb-to-hsv-in-glsl
vec3 rgb2hsv(vec3 c) {
    vec4 K = vec4(0.f, -1.f / 3.f, 2.f / 3.f, -1.f);
    vec4 p = mix(vec4(c.bg, K.wz), vec4(c.gb, K.xy), step(c.b, c.g));
    vec4 q = mix(vec4(p.xyw, c.r), vec4(c.r, p.yzx), step(p.x, c.r));
    float d = q.x - min(q.w, q.y);
    return vec3(abs(q.z + (q.w - q.y) / (6.f * d + 1.0e-10)), d / (q.x + 1.0e-10), q.x);
}
vec3 hsv2rgb(vec3 c) {
    vec4 K = vec4(1., 2. / 3., 1. / 3., 3.);
    vec3 p = abs(fract(c.xxx + K.xyz) * 6. - K.www);
    return c.z * mix(K.xxx, clamp(p - K.xxx, 0., 1.), c.y);
}

vec3 lookup(in vec3 textureColor) {
    textureColor = clamp(textureColor, 0.0, 1.0);

    highp float blueColor = textureColor.b * 63.0;

    highp vec2 quad1;
    quad1.y = floor(floor(blueColor) / 8.0);
    quad1.x = floor(blueColor) - (quad1.y * 8.0);

    highp vec2 quad2;
    quad2.y = floor(ceil(blueColor) / 8.0);
    quad2.x = ceil(blueColor) - (quad2.y * 8.0);

    highp vec2 texPos1;
    texPos1.x = (quad1.x * 0.125) + 0.5/512.0 + ((0.125 - 1.0/512.0) * textureColor.r);
    texPos1.y = (quad1.y * 0.125) + 0.5/512.0 + ((0.125 - 1.0/512.0) * textureColor.g);

    highp vec2 texPos2;
    texPos2.x = (quad2.x * 0.125) + 0.5/512.0 + ((0.125 - 1.0/512.0) * textureColor.r);
    texPos2.y = (quad2.y * 0.125) + 0.5/512.0 + ((0.125 - 1.0/512.0) * textureColor.g);

    highp vec3 newColor1 = texture(LookupTable, texPos1).rgb;
    highp vec3 newColor2 = texture(LookupTable, texPos2).rgb;

    highp vec3 newColor = (mix(newColor1, newColor2, fract(blueColor)));
    return newColor;
}
    #define TONEMAP_GAMMA (1.5)
float tonemapSin(float ch) {
    return ch < 0.0001f
    ? ch
    : 0.5f - 0.5f * cos(pow(ch, 1.0/TONEMAP_GAMMA) * PI);
}

vec2 tonemapSin(vec2 ch) {
    return vec2(tonemapSin(ch.x), tonemapSin(ch.y));
}

vec3 tonemap(vec3 rgb) {
    vec3 sorted = rgb;

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
    minmax = pow(minmax, vec2(3.f)) * toneMapCoeffs.x +
    pow(minmax, vec2(2.f)) * toneMapCoeffs.y +
    minmax * toneMapCoeffs.z +
    toneMapCoeffs.w;

    //minmax = mix(minmax, minmaxsin, 0.9f);

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
        finalRGB.r = minmax.x;
        finalRGB.g = newMid;
        finalRGB.b = minmax.y;
        break;
        case 1: // g >= b >= r
        finalRGB.r = minmax.x;
        finalRGB.b = newMid;
        finalRGB.g = minmax.y;
        break;
        case 2: // b >= r >= g
        finalRGB.g = minmax.x;
        finalRGB.r = newMid;
        finalRGB.b = minmax.y;
        break;
        case 3: // g >= r >= b
        finalRGB.b = minmax.x;
        finalRGB.r = newMid;
        finalRGB.g = minmax.y;
        break;
        case 6: // r >= b >= g
        finalRGB.g = minmax.x;
        finalRGB.b = newMid;
        finalRGB.r = minmax.y;
        break;
        case 7: // r >= g >= b
        finalRGB.b = minmax.x;
        finalRGB.g = newMid;
        finalRGB.r = minmax.y;
        break;
    }
    return finalRGB;
}
void main() {
    ivec2 xy = ivec2(gl_FragCoord.xy);
    vec3 sRGB = texelFetch(InputBuffer, xy, 0).rgb;

    sRGB = clamp(sRGB,0.0,1.0);
    float br = dot(sRGB.rgb, vec3(0.299, 0.587, 0.114));

    //sRGB=sRGB/br;

    float pbr = br;
    float HistEq = texture(Histogram, vec2(1.0/8192.0 + br*(1.0-1.0/256.0), 0.5f)).r;
    sRGB.r = texture(Histogram, vec2(1.0/8192.0 + sRGB.r*(1.0-1.0/256.0), 0.5f)).r;
    sRGB.g = texture(Histogram, vec2(1.0/8192.0 + sRGB.g*(1.0-1.0/256.0), 0.5f)).r;
    sRGB.b = texture(Histogram, vec2(1.0/8192.0 + sRGB.b*(1.0-1.0/256.0), 0.5f)).r;
    //Limit eq
    //HistEq = clamp(HistEq, 0.0, 5.0);

    //Equalization factor
    //float factor = 1.0;
    //factor*=1.0-clamp(br-0.6, 0.0, 0.4)/0.4;
    //factor*=1.0-clamp(br-0.4,0.0,0.4)/0.3;
    //factor = clamp(factor, 0.0, 1.0);


    //if(br > EPS) br = mix(br,br*pow(HistEq/br,HistFactor),factor);
    //br = mix(mix(HistEq, sqrt(HistEq), BR),br,0.2);
    //br = mix(HistEq,br,0.0);

    //br=HistEq;

    //if(br > EPS)
    //br = mix(br,HistEq,factor);
    //br = texture(Equalizing, vec2(1.0/512.0 + br*(1.0-1.0/256.0), 0.5f)).r;
    //br = pow(br,Equalize);

    //Undersaturate shadows
    //float undersat = max(0.12-br, 0.0)*1.5/0.12;
    //sRGB.b = br;
    //sRGB = hsv2rgb(sRGB);
    //sRGB += (sRGB.r+sRGB.g+sRGB.b)*undersat/3.0;

    //sRGB*=br;
    sRGB = clamp((sRGB-vec3(BL2))/(vec3(1.0)-vec3(BL2)),0.0,1.0);
    //sRGB /= luminocity(sRGB);
    //sRGB*=pbr;
    //sRGB*=br;
    //sRGB = clamp(sRGB-vec3(BL2),0.0,1.0);
    //sRGB = (tonemap((sRGB)));
    //Output = mix(sRGB*sRGB*sRGB*-3.7101449 + sRGB*sRGB*5.4910145 - sRGB*0.7808696,sRGB,min(sRGB*0.6+0.55,1.0));

    //Output = mix(sRGB*sRGB*sRGB*-1.6 + sRGB*sRGB*2.55 - sRGB*0.15,sRGB,min(sRGB*0.5+0.4,1.0));

    Output.rgb = clamp(sRGB,0.0,1.0);
    Output.rgb = tonemap(Output.rgb);
    #if LUT == 1
    Output.rgb = lookup(Output.rgb);
    #endif
}
