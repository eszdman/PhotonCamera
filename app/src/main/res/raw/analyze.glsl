#version 300 es
precision highp sampler2D;
precision highp float;
uniform sampler2D InputBuffer;
uniform sampler2D LookupTable;
uniform int stp;
out vec4 Output;
#define SAMPLING (1)
#define SIGMA 0
#define WP (1.0,1.0,1.0)
#define ANALYZEINTENSE 0.0
#define LUT 0
#define luminocity(x) dot(x.rgb, vec3(0.299, 0.587, 0.114))
#import xyztoxyy
#import xyytoxyz
#import xyztolab
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
void main() {
    ivec2 xy = ivec2(vec2(SAMPLING) * vec2(gl_FragCoord.xy));
    vec3[9]inp;
    if(stp == 0){
        for (int i = 0; i < 9; i++) {
            vec3 rgbin = texelFetch(InputBuffer, xy + 2*ivec2((i % 3) - 1, (i / 3) - 1), 0).rgb;
            rgbin = (rgbin);
            inp[i] = XYZtoxyY(rgbin);
        }


        vec3 mean;
        int cnt = 0;
        for (int i = 0; i < 9; i++) {
            if(inp[4].r+inp[4].g+inp[4].b < 0.000001){
                mean += inp[i];
                cnt++;
            }
        }
        mean/=float(cnt);
        if(inp[4].r+inp[4].g+inp[4].b < 0.000001) inp[4] = mean;
        mean = vec3(0.0);
        #if SIGMA == 1
        vec3 sigma;
        for (int i = 0; i < 9; i++) {
            mean += inp[i];
        }
        mean /= 9.f;
        for (int i = 0; i < 9; i++) {
            vec3 diff = mean - inp[i];
            sigma += diff * diff;
        }
        float z = inp[4].z;
        z = mix(mix(z, z*z, ANALYZEINTENSE),z,z);
        Output = vec4(sqrt(sigma / 9.f), z);
        #else
        vec3 inv = (texelFetch(InputBuffer, xy, 0).rgb);
        float z = inp[4].z;
        Output = vec4(xyYtoXYZ(inp[4].rgb), z);
        Output = mix(mix(Output,Output*Output,ANALYZEINTENSE),Output,z);
        //Output.rgb = lookup(Output.rgb);
        Output.a = XYZtoxyY(lookup(Output.rgb)).z;
        #endif

    } else {
        //vec3 inp;
        vec3 inp = texelFetch(InputBuffer, xy, 0).rgb;
        for(int i = -2;i<2;i++){
            for(int j = -2;j<2;j++){
                inp = min(inp,texelFetch(InputBuffer, xy + ivec2(i,j)*int(float(SAMPLING/2)*0.24), 0).rgb);
            }
        }
        Output = vec4(inp/vec3(WP),1.0);
    }
}
