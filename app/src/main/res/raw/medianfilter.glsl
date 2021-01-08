#version 300 es
precision mediump float;
precision mediump sampler2D;
// Input texture
uniform sampler2D InputBuffer;
#define TRANSPOSE (1,1)
#define s2(a, b)				temp = a; a = min(a, b); b = max(temp, b);
#define mn3(a, b, c)			s2(a, b); s2(a, c);
#define mx3(a, b, c)			s2(b, c); s2(a, c);

#define mnmx3(a, b, c)			mx3(a, b, c); s2(a, b);                                   // 3 exchanges
#define mnmx4(a, b, c, d)		s2(a, b); s2(c, d); s2(a, c); s2(b, d);                   // 4 exchanges
#define mnmx5(a, b, c, d, e)	s2(a, b); s2(c, d); mn3(a, c, e); mx3(b, d, e);           // 6 exchanges
#define mnmx6(a, b, c, d, e, f) s2(a, d); s2(b, e); s2(c, f); mn3(a, b, c); mx3(d, e, f); // 7 exchanges
out vec3 Output;
uniform int yOffset;
#import median
void main() {
    ivec2 xy = ivec2(gl_FragCoord.xy);
    xy+=ivec2(0,yOffset);
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
}