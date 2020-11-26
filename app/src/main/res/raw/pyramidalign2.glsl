#version 300 es
precision mediump float;
precision mediump sampler2D;
precision mediump usampler2D;
precision mediump isampler2D;
uniform sampler2D InputBuffer;
uniform sampler2D MainBuffer;
uniform sampler2D AlignVectors;
uniform int prevLayerScale;
uniform int yOffset;
out vec3 Output;
#define TILESIZE (32)
#define TILESCALE (TILESIZE/2)
#define oversizek (1)
#define MAXX (4*3)
#define MAXY (3*3)
#define FLT_MAX 3.402823466e+38
#define M_PI 3.1415926535897932384626433832795
#define distribute(x,dev,sigma) ((exp(-(x-dev) * (x-dev) / (2.0 * sigma * sigma)) / (sqrt(2.0 * M_PI) * sigma)))
void main() {
    ivec2 xy = ivec2(gl_FragCoord.xy);
    xy+=ivec2(0,yOffset);
    ivec2 prevAlign = ivec2(0,0);
    if (prevLayerScale != 0) {
        prevAlign = ivec2(texelFetch(AlignVectors, xy / prevLayerScale, 0).xy)*(prevLayerScale);
    }
    ivec2 xyFrame = ivec2(gl_FragCoord.xy*float(TILESCALE));
    float dist = 0.0;
    float mindist = FLT_MAX;
    ivec2 outalign = ivec2(0,0);
    ivec2 shift = ivec2(0,0);
    vec4 in1;
    vec4 in2;
    for(int h = 0;h<MAXY;h++){
        for(int w = 0;w<MAXX;w++){
            /*for(int h0=-TILESIZE/3;h0<TILESIZE/3;h0++){
                for(int w0=-TILESIZE/3;w0<TILESIZE/3;w0++){
                    in1 = texelFetch(MainBuffer, (xyFrame+ivec2(w0, h0)), 0);
                    //if(length(in1) < 1.0/1000.0) continue;
                    in2 = texelFetch(InputBuffer, (xyFrame+shift+ivec2(w0, h0)), 0);
                    //in1 = abs((in1)-(in2));
                    //dist+=(in1.a);
                    dist+=distribute(in1.a, in2.a,0.2);
                    //dist+=distribute(in1.r, in2.r,0.2);
                    //dist+=distribute(in1.g, in2.g,0.2);
                    //dist+=distribute(in1.b, in2.b,0.2);
                }
            }*/
            for(int i =-1;i<1;i+=2){
                for (int j =-1;j<1;j+=2){
                    shift = ivec2(w*i, h*j)+prevAlign;
                    for (int h0=-TILESIZE/2;h0<TILESIZE/2;h0++){
                        in1 = texelFetch(MainBuffer, (xyFrame+ivec2(0, h0)), 0);
                        in2 = texelFetch(InputBuffer, (xyFrame+shift+ivec2(0, h0)), 0);
                        //in1 = abs((in1)-(in2));
                        //dist+=in1.a+in1.r+in1.g+in1.b;
                        dist+=distribute(in1.a, in2.a, 0.1);
                    }
                    for (int w0=-TILESIZE/2;w0<TILESIZE/2;w0++){
                        in1 = texelFetch(MainBuffer, (xyFrame+ivec2(w0, 0)), 0);
                        in2 = texelFetch(InputBuffer, (xyFrame+shift+ivec2(w0, 0)), 0);
                        //in1 = abs((in1)-(in2));
                        //dist+=in1.a+in1.r+in1.g+in1.b;
                        dist+=distribute(in1.a, in2.a, 0.1);
                    }
                    dist/=float(TILESIZE)*2.0;
                    //dist/=((2.0 * float(TILESIZE/2) + 1.0) * (2.0 * float(TILESIZE/2) + 1.0));

                    if (dist < 0.2){
                        outalign = shift;
                        Output = vec3(outalign.x,outalign.y,0)/float(TILESIZE*16);
                        Output.b = dist;
                        return;
                    } else {
                        if(dist < mindist){
                            mindist = dist;
                            outalign = shift;
                        }
                    }
                    dist = 0.0;
                }
            }
        }
    }
    Output = vec3(outalign.x,outalign.y,0)/float(TILESIZE*16);
    Output.b = mindist;
    //Output = vec2(1000,1000);
}
