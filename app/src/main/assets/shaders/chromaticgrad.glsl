precision mediump float;
precision mediump sampler2D;
uniform sampler2D InputBuffer;
out vec4 Output;
void main() {
    ivec2 xy = ivec2(gl_FragCoord.xy);
    vec2 rbx = texelFetch(InputBuffer, xy-ivec2(2,0), 0).rb - texelFetch(InputBuffer, xy+ivec2(2,0), 0).rb;
    vec2 rby = texelFetch(InputBuffer, xy-ivec2(0,2), 0).rb - texelFetch(InputBuffer, xy+ivec2(0,2), 0).rb;
    //R grad xy
    Output.rg = vec2(rbx.r,rby.r);
    //B grad xy
    Output.ba = vec2(rbx.g,rby.g);
}