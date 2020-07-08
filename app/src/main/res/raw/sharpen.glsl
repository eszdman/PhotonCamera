#version 300 es
precision mediump float;
precision mediump sampler2D;
uniform sampler2D InputBuffer;
uniform int yOffset;
uniform float strength;

out vec4 Output;
void main() {
    ivec2 xy = ivec2(gl_FragCoord.xy);
    xy+=ivec2(0,yOffset);
    //Box blur
    vec4 mask = vec4(0.0);
    vec4 cur = (texelFetch(InputBuffer, (xy), 0));
    for(int w =-1; w<2;w++){
    for(int h =-1;h<2;h++){
        mask+=vec4(texelFetch(InputBuffer, (xy+ivec2(h,w)), 0));
       }
    }
    mask/=9.;
    mask =(cur-mask);
    mask*=strength;
    cur+=mask;
    Output = cur;
}