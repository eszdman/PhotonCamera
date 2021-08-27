#version 300 es
precision mediump float;
precision mediump sampler2D;
// Input texture
uniform sampler2D InputBuffer;
uniform sampler2D GradBuffer;
#define MSIZE 9
#define KSIZE (MSIZE-1)/2

#define TRANSPOSE (1,1)
#define SIZE 9
#define PI 3.1415926535897932384626433832795
out vec3 Output;
uniform int yOffset;
float normpdf(in float x, in float sigma)
{
    return 0.39894*exp(-0.5*x*x/(sigma*sigma))/sigma;
}
#import median
void main() {
    ivec2 xy = ivec2(gl_FragCoord.xy);
    xy+=ivec2(0,yOffset);
    vec3 v[9];
    float sigX = 3.5;
    vec2 dxy;
    float sum = 0.0001;
    for (int i=-KSIZE; i <= KSIZE; i++){
        float k0 = normpdf(float(i),sigX);
        for (int j=-KSIZE; j <= KSIZE; j++){
            float k = normpdf(float(j),sigX)*k0;
            vec2 temp = k*texelFetch(GradBuffer, xy+ivec2(i,j), 0).rg;
            dxy+=temp;
            sum+=k;
        }
    }
    //dxy/=sum;
    //float angle = atan(dxy.y,dxy.x)+PI;
    vec2 movement = vec2(dxy.y,dxy.x)*1.415/sqrt(dxy.y*dxy.y+dxy.x*dxy.x);
    #if SIZE == 9

    for(int i = -4; i<=4;i++){
        v[i+4] = vec3(texelFetch(InputBuffer, ivec2(vec2(xy)+movement*float(i)), 0).rgb);
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
            v[(dX) * 2 + (dY)] = vec3(texelFetch(InputBuffer, ivec2(vec2(xy)+movement*vec2(offset)), 0).rgb);
        }
    }
    v[4] = (v[0]+v[1]+v[2]+v[3])/4.0;
    Output = median5(v);
    #endif
}