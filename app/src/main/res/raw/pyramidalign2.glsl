#version 300 es
precision mediump float;
precision mediump sampler2D;
precision mediump usampler2D;
uniform sampler2D InputBuffer;
uniform sampler2D MainBuffer;
uniform sampler2D AlignVectors;
uniform int prevLayerScale;
uniform int yOffset;
out ivec2 Output;
#define TILESIZE (16)
#define oversizek (1)
#define MAXX (4*4)
#define MAXY (3*4)
#define FLT_MAX 3.402823466e+38

void main() {
    ivec2 xy = ivec2(gl_FragCoord.xy);
    xy+=ivec2(0,yOffset);
    ivec2 prevAlign = ivec2(0,0);
    if (prevLayerScale != 0) {
        prevAlign = ivec2(texelFetch(AlignVectors, xy / prevLayerScale, 0).xy)*(prevLayerScale);
    }
    ivec2 xyFrame = xy*TILESIZE;
    float dist = 0.0;
    float mindist = FLT_MAX;
    ivec2 outalign = ivec2(0,0);
    ivec2 shift = ivec2(0,0);
    vec4 in1;
    vec4 in2;
    int cnt = 0;
    for(int h = -MAXY;h<MAXY;h++){
        for(int w = -MAXX;w<MAXX;w++){
            shift = ivec2(w,h)+prevAlign;
            for(int h0=-2;h0<TILESIZE;h0++){
                for(int w0=-2;w0<TILESIZE;w++){
                    in1 = texelFetch(MainBuffer, (xyFrame+ivec2(w0, h0)), 0);
                    //if(length(in1) < 1.0/1000.0) continue;
                    in2 = texelFetch(InputBuffer, (xyFrame+shift+ivec2(w0, h0)), 0);
                    in1 = abs((in1)-(in2));
                    dist+=(in1.r+in1.g+in1.b)+(in1.a);
                    cnt++;
                }
            }
            if(cnt == 0) {
                dist = FLT_MAX;
            } else dist/=float(cnt);
            if(dist < mindist){
                mindist = dist;
                outalign = shift;
            }
            dist = 0.0;
            cnt = 0;
        }
    }
    Output = (outalign);
    Output = ivec2(1000,1000);
}
