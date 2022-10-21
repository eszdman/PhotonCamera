
precision mediump float;
precision mediump sampler2D;
// Input texture
uniform sampler2D InputBuffer;
#define RESIZE 4.0
#define SIZE 9
out vec3 Output;
#import median
void main() {
    ivec2 xy = ivec2(gl_FragCoord.xy*RESIZE);
    #if SIZE == 9
    vec3 v[9];
    for(int dX = -1; dX <= 1; ++dX) {
        for (int dY = -1; dY <= 1; ++dY) {
            ivec2 offset = ivec2((dX), (dY));
            v[(dX + 1) * 3 + (dY + 1)] = vec3(texelFetch(InputBuffer, xy + offset, 0).rgb);
        }
    }
    Output = median9(v);
    #endif
    #if SIZE == 4
    vec3 v[5];
    for(int dX = -1; dX < 1; dX++) {
        for (int dY = -1; dY < 1; dY++) {
            ivec2 offset = ivec2((dX), (dY));
            v[(dX+1) * 2 + (dY+1)] = vec3(texelFetch(InputBuffer, xy + offset, 0).rgb);
        }
    }
    v[4] = (v[0]+v[1]+v[2]+v[3])/4.0;
    Output = median5(v);
    #endif
}
