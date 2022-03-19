precision mediump float;
precision mediump sampler2D;
uniform sampler2D InputBuffer;
uniform int yOffset;

out vec4 Output;
void main() {
    ivec2 xy = ivec2(gl_FragCoord.xy);
    xy+=ivec2(0,yOffset);
    vec4 inbuff = texelFetch(InputBuffer,xy,0);
    //outp/=65535.0;
    Output = inbuff;
}