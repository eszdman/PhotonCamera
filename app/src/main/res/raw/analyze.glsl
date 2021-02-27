#version 300 es
precision highp sampler2D;
precision highp float;
uniform sampler2D InputBuffer;
uniform int stp;
out vec4 Output;
#define SAMPLING (1)
#define SIGMA 0
#define WP (1.0,1.0,1.0)
#define BR (0.6)
#define luminocity(x) dot(x.rgb, vec3(0.299, 0.587, 0.114))
#import xyztoxyy
void main() {
    ivec2 xy = ivec2(vec2(SAMPLING) * vec2(gl_FragCoord.xy));
    vec3[9]inp;
    if(stp == 0){
        for (int i = 0; i < 9; i++) {
            inp[i] = XYZtoxyY(texelFetch(InputBuffer, xy + 2*ivec2((i % 3) - 1, (i / 3) - 1), 0).rgb);
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
        Output = vec4(sqrt(sigma / 9.f), mix(z, z*z, BR));
        #else
        vec3 inv = texelFetch(InputBuffer, xy, 0).rgb;
        float z = inp[4].z;
        Output = vec4(inv.r,inv.g,inv.b, z);
        Output = mix(Output,Output*Output,BR);
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
