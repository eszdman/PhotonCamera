
precision mediump float;
precision mediump sampler2D;
uniform sampler2D WeightsIn;
uniform sampler2D WeightsOut;
out float Output;
void main() {
    ivec2 xy = ivec2(gl_FragCoord.xy);
    Output = texelFetch(WeightsIn, xy, 0).r+texelFetch(WeightsOut, xy, 0).r;
}