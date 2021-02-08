#version 300 es
precision highp float;
precision highp sampler2D;
uniform sampler2D InputBuffer;

//#define luminocity(x) dot(x.rgb, vec3(0.299, 0.587, 0.114))
#define luminocity(x) x.g
//////////////////////////////////////////////////////////////////////////////////
out vec3 Output;
void main() {
    ivec2 xy = ivec2(gl_FragCoord.xy);
    ivec2 shift = xy%2;
    ivec2 rxy = xy-shift;
    vec3 colours = texelFetch(InputBuffer, rxy,0).rgb;
    if(shift.x == 0 && shift.y == 0){
        Output = colours;
    } else if(shift.x == 1) {
        if(shift.y == 1){
            //Quartal
            colours+=texelFetch(InputBuffer, rxy+ivec2(0,2),0).rgb;
            colours+=texelFetch(InputBuffer, rxy+ivec2(2,0),0).rgb;
            colours+=texelFetch(InputBuffer, rxy+ivec2(2,2),0).rgb;
            colours+=0.00001;
            colours/=luminocity(colours);
            Output = colours*luminocity(texelFetch(InputBuffer, xy,0).rgb);
        } else {
            //Horizontal
            colours+=texelFetch(InputBuffer, rxy+ivec2(2,0),0).rgb;
            colours+=0.00001;
            colours/=luminocity(colours);
            Output = colours*luminocity(texelFetch(InputBuffer, xy,0).rgb);
        }
    } else {
        //Vertical
        colours+=texelFetch(InputBuffer, rxy+ivec2(0,2),0).rgb;
        colours+=0.00001;
        colours/=luminocity(colours);
        Output = colours*luminocity(texelFetch(InputBuffer, xy,0).rgb);
    }
}
