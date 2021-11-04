
precision mediump float;
precision mediump sampler2D;
precision mediump usampler2D;
uniform sampler2D InputBuffer;
uniform sampler2D MainBuffer;
uniform sampler2D AlignVectors;
uniform int yOffset;
uniform ivec2 maxSize;
uniform ivec2 minSize;
uniform ivec2 size;
uniform int Mpy;
out vec2 Output;
#define M_PI 3.1415926535897932384626433832795f
#define FLT_MAX 3.402823466e+38
#define TILESIZE (128)
#define oversizek (1)
#define MAXX (4*2)
#define MAXY (3*2)

/*float cmpTiles(ivec2 xy,int tSize,ivec2 shift){
    for(int h=start.y; h<end.y; h++){
        for(int w=start.x;w<end.x;w++){
            in1 = texelFetch(MainBuffer, (ivec2(w, h)), 0);
            in2 = texelFetch(InputBuffer, (ivec2(w, h)), 0);
            in1 = abs((in1)-(in2));
            dist+=(in1.r+in1.g+in1.b)+(in1.a)*0.1;
            cnt++;
        }
    }
    if(dist >= 0.0) {
        return dist/float(cnt);
    } else return FLT_MAX;
}*/

void main() {
    ivec2 xy = ivec2(gl_FragCoord.xy);
    xy+=ivec2(0,yOffset);
    ivec2 align = ivec2(0,0);
    int mpy = Mpy;
    int tSize = TILESIZE/mpy;
    int nsize = max(4*4,tSize*oversizek);
    ivec2 prevAlign = ivec2(texelFetch(AlignVectors, (xy), 0).xy);

    float dist = 0.0;
    int cnt = 0;

    ivec2 texsize = ivec2(size);
    ivec2 start;
    ivec2 end;
    vec4 in1;
    vec4 in2;
    ivec2 shift;
    float lumDist = FLT_MAX;
    //if(xy.x < maxSize.x && xy.y < maxSize.y && xy.x > minSize.x && xy.y > minSize.y)
    for(int h =-MAXY;h<MAXY;h++){
        for(int w = -MAXX;w<MAXX;w++){

            shift = ivec2(w,h)+prevAlign/mpy;
            texsize = min(texsize,texsize+shift);
            start = clamp(xy*tSize-2,ivec2(0),texsize-1);
            end = clamp(xy*tSize+nsize,ivec2(0),texsize);
            //start = xy*tSize-2;
            //end = xy*tSize+nsize;

            for(int h2=start.y; h2<end.y; h2++){
                for(int w2=start.x;w2<end.x;w2++){
                    in1 = texelFetch(MainBuffer, (ivec2(w2, h2)), 0);
                    in2 = texelFetch(InputBuffer, (shift+ivec2(w2, h2)), 0);
                    in1 = abs((in1)-(in2));
                    dist+=(in1.r+in1.g+in1.b)+(in1.a)*0.1;
                    cnt++;
                }
            }
            if(cnt != 0) {
                dist/=float(cnt);
            } else dist = FLT_MAX;

            //float inp = cmpTiles(xy*tSize,tSize,ivec2(w,h)+prevAlign/mpy);
            if(dist < lumDist){
                lumDist = dist;
                align = ivec2(w,h);
            }
            dist = 0.0;
            cnt=0;
        }
    }
    Output = vec2(prevAlign + mpy*align);
}
