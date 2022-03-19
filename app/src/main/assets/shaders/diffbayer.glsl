precision mediump float;
precision mediump sampler2D;
uniform sampler2D InputBuffer;
out vec2 Output;
#define INSIZE 1,1
void main() {
    ivec2 xy = ivec2(gl_FragCoord.xy);
    if(xy.x < 1 || xy.y < 1 || xy.x > ivec2(INSIZE).x-1 || xy.y > ivec2(INSIZE).y-1) return;
    ivec2 shift = xy%2;
    ivec2 mvx = ivec2(2,0);
    ivec2 mvy = ivec2(0,2);
    if(shift.x+shift.y ==  1){
        mvx = ivec2(-1,1);
        mvy = ivec2(1,1);
    }
    //Output.x = texelFetch(InputBuffer, clamp(xy-mvx,ivec2(0,0),ivec2(INSIZE)),0).r-texelFetch(InputBuffer, clamp(xy+mvx,ivec2(0,0),ivec2(INSIZE)),0).r;
    //Output.y = texelFetch(InputBuffer, clamp(xy-mvy,ivec2(0,0),ivec2(INSIZE)),0).r-texelFetch(InputBuffer, clamp(xy+mvy,ivec2(0,0),ivec2(INSIZE)),0).r;

    Output.x = texelFetch(InputBuffer, xy-mvx,0).x-texelFetch(InputBuffer, xy+mvx,0).x;
    Output.y = texelFetch(InputBuffer, xy-mvy,0).x-texelFetch(InputBuffer, xy+mvy,0).x;
    Output/=2.0;
}