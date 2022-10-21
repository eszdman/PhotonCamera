
precision highp float;
precision mediump usampler2D;
uniform usampler2D InputBuffer;
uniform float WhiteLevel;
uniform int yOffset;
out float Output;
#define MPY (1.0)
#define BL 0.0,0.0,0.0,0.0
#define WP 1.0,1.0,1.0
#define BAYER 0
void main() {
    ivec2 xy = ivec2(gl_FragCoord.xy);
    xy+=ivec2(0,yOffset);
    vec4 black = vec4(BL);
    float outp;
    ivec2 xy2 = (xy-ivec2(BAYER%2,BAYER/2))%2;

    if(xy2.x+xy2.y == 0){
        float blc = black.r;
        outp = ((float(texelFetch(InputBuffer, (xy), 0).x)-blc)/(WhiteLevel-blc));
    } else if(xy2.x+xy2.y == 1){
        float blc = (black.g+black.b)/2.0;
        outp = ((float(texelFetch(InputBuffer, (xy), 0).x)-blc)/(WhiteLevel-blc));
    } else {
        float blc = black.a;
        outp = ((float(texelFetch(InputBuffer, (xy), 0).x)-blc)/(WhiteLevel-blc));
    }



    //outp = ((float(texelFetch(InputBuffer, (xy), 0).x))/(WhiteLevel));
    Output = outp*MPY;
}