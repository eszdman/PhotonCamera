#version 300 es
precision mediump float;
precision mediump sampler2D;
uniform sampler2D InputBuffer;
uniform sampler2D BlurBuffer;
uniform int yOffset;
uniform float size;
uniform float strength;
out vec3 Output;
//#define depthMin (0.012)
#define depthMin (0.006)
#define depthMax (0.890)
#define colour (0.2)
#define size1 (1.1)
#define MSIZE1 3
#define kSize ((MSIZE1-1)/2)
#import median
void main() {
    ivec2 xy = ivec2(gl_FragCoord.xy);
    xy+=ivec2(0,yOffset);
    vec3 mask = vec3(0.0);
    vec3 cur = (texelFetch(InputBuffer, (xy), 0).rgb);
    vec3 v[9];
    for (int i=-kSize; i <= kSize; ++i){
       for (int j=-kSize; j <= kSize; ++j){
           v[(i+kSize)*3 + j+kSize] = vec3(texelFetch(BlurBuffer, (xy+ivec2(i,j)), 0).rgb);
       }
    }

    mask=median9(v);
    mask =(vec3(texelFetch(BlurBuffer, (xy), 0).rgb)-mask);
    //mask=clamp(mask,-depthMax,depthMax);
    if(abs(mask.r+mask.b+mask.g) < depthMin) mask*=0.;
    //cur-=mask;
    cur+=(mask.r+mask.g+mask.b)*((strength*4.0/3.));
    cur = clamp(cur,0.0,1.0);
    Output = cur;
}