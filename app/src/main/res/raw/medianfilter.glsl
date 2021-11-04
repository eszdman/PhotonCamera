
precision mediump float;
precision mediump sampler2D;
// Input texture
uniform sampler2D InputBuffer;
uniform sampler2D GradBuffer;
#define TRANSPOSE (1,1)
#define SIZE 9
out vec3 Output;
uniform int yOffset;
#import median
void main() {
    ivec2 xy = ivec2(gl_FragCoord.xy);
    xy+=ivec2(0,yOffset);
    #if SIZE == 9
    vec3 v[9];

    //vec4 c = vec4(texelFetch(InputBuffer, xy, 0));
    // Add the pixels which make up our window to the pixel array.
    for(int dX = -1; dX <= 1; ++dX) {
        for (int dY = -1; dY <= 1; ++dY) {
            ivec2 offset = ivec2((dX), (dY));
            // If a pixel in the window is located at (x+dX, y+dY), put it at index (dX + R)(2R + 1) + (dY + R) of the
            // pixel array. This will fill the pixel array, with the top left pixel of the window at pixel[0] and the
            // bottom right pixel of the window at pixel[N-1].
            v[(dX + 1) * 3 + (dY + 1)] = vec3(texelFetch(InputBuffer, xy + offset*ivec2(TRANSPOSE), 0).rgb);
        }
    }
    Output = median9(v);
    #endif
    #if SIZE == 4
    vec3 v[5];
    //vec4 c = vec4(texelFetch(InputBuffer, xy, 0));
    // Add the pixels which make up our window to the pixel array.
    for(int dX = 0; dX < 2; dX++) {
        for (int dY = 0; dY < 2; dY++) {
            ivec2 offset = ivec2((dX), (dY));
            // If a pixel in the window is located at (x+dX, y+dY), put it at index (dX + R)(2R + 1) + (dY + R) of the
            // pixel array. This will fill the pixel array, with the top left pixel of the window at pixel[0] and the
            // bottom right pixel of the window at pixel[N-1].
            v[(dX) * 2 + (dY)] = vec3(texelFetch(InputBuffer, xy + offset*ivec2(TRANSPOSE), 0).rgb);
        }
    }
    v[4] = (v[0]+v[1]+v[2]+v[3])/4.0;
    Output = median5(v);
    #endif
}