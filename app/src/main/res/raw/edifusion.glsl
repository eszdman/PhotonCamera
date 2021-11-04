
precision highp float;
precision highp sampler2D;
uniform sampler2D InputBuffer;
uniform sampler2D BaseBuffer;

out float Output;
//Generate bigger centers for InputBuffer
void main() {
    ivec2 xy = ivec2(gl_FragCoord.xy);
    ivec2 xy2 = xy/2;
    ivec2 shift = (xy%2);
    if((shift.x+shift.y) == 2){
        float gradx = abs(texelFetch(BaseBuffer, xy+ivec2(-1,0), 0) - texelFetch(BaseBuffer, xy+ivec2(1,0), 0));
        float grady = abs(texelFetch(BaseBuffer, xy+ivec2(0,-1), 0) - texelFetch(BaseBuffer, xy+ivec2(0,1), 0));
        float lup =  texelFetch(InputBuffer, xy2, 0);
        float rup =  texelFetch(InputBuffer, xy2+ivec2(1,0), 0);
        float ld =  texelFetch(InputBuffer, xy2+ivec2(1,0), 0);
        float rd =  texelFetch(InputBuffer, xy2+ivec2(0,1), 0);
        if(gradx > grady){
            Output =
        } else {

        }
    } else {
        Output = texelFetch(InputBuffer, xy2, 0).r;
    }
}
