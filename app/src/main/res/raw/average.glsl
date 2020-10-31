#version 300 es
precision highp float;
precision mediump usampler2D;
//precision mediump sampler2D;
uniform sampler2D InputBuffer;
uniform usampler2D InputBuffer2;
uniform int unlimitedcount;
uniform vec4 blackLevel;
uniform int first;
uniform int yOffset;
out float Output;

void main() {
    ivec2 xy = ivec2(gl_FragCoord.xy);
    //xy.x*=2;
    xy+=ivec2(0,yOffset);
    //float rawpart = float(texelFetch(InputBuffer, (xy), 0).x)-(blackLevel.g+0.5);
    //float rawpart2 = float(texelFetch(InputBuffer2, (xy), 0).x)-(blackLevel.g+0.5);
    //Output = uint(((rawpart*float((unlimitedcount-1))) +(rawpart2))/float(unlimitedcount) + blackLevel.g+0.5);
    if(first == 1){
        Output = float(
        float(texelFetch(InputBuffer2, (xy), 0).x)
        //+ (blackLevel.g-1.5)
        //)
        );
    } else {
        Output = float(
        //floor(
        mix(
        float(texelFetch(InputBuffer, (xy), 0).x)
        //-(blackLevel.g-1.5)
        ,
        float(texelFetch(InputBuffer2, (xy), 0).x)
        //-(blackLevel.g-1.5)
        ,
        1.f/float(unlimitedcount)
        )
        //+ (blackLevel.g-1.5)
        //)
        );
    }

}