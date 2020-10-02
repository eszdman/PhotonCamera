#version 300 es
precision mediump float;
precision mediump sampler2D;
uniform sampler2D InputBuffer;
uniform vec3 colorvec;
uniform int yOffset;
out vec4 Output;
void main() {
    ivec2 xy = ivec2(gl_FragCoord.xy);
    xy+=ivec2(0,yOffset);
    vec3 inp = texelFetch(InputBuffer, xy, 0).rgb;
    //float br = length(inp);
    //inp = normalize(inp*colorvec);
    Output = vec4(inp*colorvec,1.0);
}
