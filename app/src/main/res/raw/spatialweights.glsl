#version 300 es
precision mediump float;
precision mediump sampler2D;
uniform sampler2D InputBuffer22;
uniform sampler2D MainBuffer22;
uniform sampler2D AlignVectors;
uniform uvec2 rawsize;
uniform int yOffset;
#define MIN_NOISE 0.1f
#define MAX_NOISE 0.7f
#define TILESIZE (256)
out float Output;
void main() {
    ivec2 xy = ivec2(gl_FragCoord.xy);
    xy+=ivec2(0,yOffset);
    vec2 alignf = vec2(texelFetch(AlignVectors, xy,0).xy);
    xy*=TILESIZE/2;
    xy = clamp(xy,ivec2(0),(ivec2(rawsize)/2) - (TILESIZE/(2)));
    ivec2 align = ivec2(alignf/2.0)*2;
    ivec2 aligned = (xy+align);
    aligned = clamp((aligned/2),ivec2(0,0),(ivec2(rawsize)/2) - (TILESIZE/(2)));
    float dist = 0.0;
    for(int h=0; h<TILESIZE/(2); h++){
        for(int w=0;w<TILESIZE/(2);w+=4){

            //Vectorized diff
            vec4 in1 = texelFetch(MainBuffer22, (xy+ivec2(w, h)), 0);
            vec4 in2 = texelFetch(InputBuffer22, aligned+ivec2(w, h), 0);
            in1 = abs((in1)-(in2));
            dist+= in1.r+in1.g+in1.b+in1.a;
        }
    }
    Output = dist/float(TILESIZE*TILESIZE/(4));
    //Output = smoothstep(MIN_NOISE, MAX_NOISE, dist);
}
