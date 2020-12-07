#version 300 es
uniform sampler2D RawBuffer;
uniform int yOffset;
out float Output;
void main() {
    ivec2 xy = ivec2(gl_FragCoord.xy);
    //xy+=ivec2(2,2);
    ivec2 fact = ivec2(ivec2(xy.x,xy.y)/2)%2;
    ivec2 shift = (ivec2(xy.x,xy.y))%2;
    if(fact.x+fact.y != 1){
        Output = texelFetch(RawBuffer, xy-shift-fact, 0).r;
    } else {
        if(!(shift.x == 1 && shift.y == 0)){
            Output = texelFetch(RawBuffer, xy-shift-fact, 0).r;
        } else {
            Output = texelFetch(RawBuffer, xy, 0).r;
        }
    }
}