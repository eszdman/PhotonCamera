#version 300 es
precision highp sampler2D;
precision highp float;
uniform sampler2D InputBuffer;
uniform int stp;
out vec4 Output;
#define SAMPLING (1)
#define BR (0.6)
#define luminocity(x) dot(x.rgb, vec3(0.299, 0.587, 0.114))
#import xyztoxyy
void main() {
    ivec2 xy = SAMPLING * ivec2(gl_FragCoord.xy);
    vec3[9]inp;
    if(stp == 0){
        for (int i = 0; i < 9; i++) {
            inp[i] = XYZtoxyY(texelFetch(InputBuffer, xy + 2*ivec2((i % 3) - 1, (i / 3) - 1), 0).rgb);
        }
        vec3 mean, sigma;
        for (int i = 0; i < 9; i++) {
            mean += inp[i];
        }
        mean /= 9.f;
        for (int i = 0; i < 9; i++) {
            vec3 diff = mean - inp[i];
            sigma += diff * diff;
        }

        float z = XYZtoxyY(texelFetch(InputBuffer, xy, 0).rgb).z;
        Output = vec4(sqrt(sigma / 9.f), mix(z, z*z, BR));
    } else {
        vec3 inp = texelFetch(InputBuffer, xy, 0).rgb;
        float br = XYZtoxyY(inp).z;
        inp/=br;
        br = mix(br,br*br,BR);
        Output = vec4(inp*br,1.0);
    }
}
