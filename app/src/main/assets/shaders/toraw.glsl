
precision highp float;
precision highp sampler2D;
uniform sampler2D InputBuffer;
uniform float whitelevel;
uniform int yOffset;
#define BL 0.0
#define BAYER 0
out uint Output;
void main() {
    ivec2 xy = ivec2(gl_FragCoord.xy);
    xy+=ivec2(0,yOffset);
    uint rawpart;// = uint((((texelFetch(InputBuffer, (xy), 0).x))*(whitelevel-float(4096))))+uint(4096);
    vec4 black = vec4(BL);
    ivec2 xy2 = (xy-ivec2(BAYER%2,BAYER/2))%2;
    if(xy2.x+xy2.y == 0){
        float blc = black.r;
        rawpart = uint((((texelFetch(InputBuffer, (xy), 0).x))*(whitelevel-blc))+blc);
    } else if(xy2.x+xy2.y == 1){
        float blc = (black.g+black.b)/2.0;
        rawpart = uint((((texelFetch(InputBuffer, (xy), 0).x))*(whitelevel-blc))+blc);
    } else {
        float blc = black.a;
        rawpart = uint((((texelFetch(InputBuffer, (xy), 0).x))*(whitelevel-blc))+blc);
    }
    Output = (rawpart);
}