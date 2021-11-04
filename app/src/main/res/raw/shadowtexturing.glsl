
precision mediump float;
precision mediump sampler2D;
precision mediump usampler2D;
uniform sampler2D InputBuffer;
uniform usampler2D InputTex;
uniform int yOffset;
out vec4 Output;
void main() {
    ivec2 xy = ivec2(gl_FragCoord.xy);
    xy+=ivec2(0,yOffset);
    float shadow = float(texelFetch(InputTex, (xy%255), 0).r)/16384.0;
    vec4 inp = texelFetch(InputBuffer, (xy), 0);
    if(length(inp.rgb) > 0.05) Output = inp; else
    Output = vec4(shadow*0.05,shadow*0.05,shadow*0.05,1.0);
}
