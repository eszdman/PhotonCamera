#version 300 es
precision mediump float;
precision mediump sampler2D;
precision mediump usampler2D;
uniform sampler2D InputBuffer;
uniform sampler2D MainBuffer;
uniform sampler2D AlignVectors;
uniform int yOffset;
uniform ivec2 maxSize;
uniform ivec2 minSize;
uniform int Mpy;
out vec2 Output;
#define FLT_MAX 3.402823466e+38
#define TILESIZE (256)
#define oversizek (2)
#define MAXX (4*2)
#define MAXY (3*2)


ivec2 mirrorOOBCoords(ivec2 coords) {
    ivec2 newCoords;
    if (coords.x < 0)
    newCoords.x = -coords.x;
    else if (coords.x >= maxSize.x)
    newCoords.x = maxSize.x - coords.x - 1;
    else
    newCoords.x = coords.x;
    if (coords.y < 0)
    newCoords.y = -coords.y;
    else if (coords.y >= maxSize.y)
    newCoords.y = maxSize.y - coords.y - 1;
    else
    newCoords.y = coords.y;
    return newCoords;
}
ivec2 LimitCoords(ivec2 coords){
    if(coords.x < 0) coords.x = 0+ coords.x%2;
    if(coords.y < 0) coords.y = 0+ coords.y%2;
    if(coords.x > maxSize.x) coords.x = maxSize.x+coords.x%2;
    if(coords.y > maxSize.y) coords.y = maxSize.y+coords.y%2;
    return coords;
}
float cmpTiles(ivec2 xy,int tSize,ivec2 shift){
    float dist = 0.0;
    int cnt = 0;
    tSize = max(2*2,tSize*oversizek);
    ivec2 shifted =  xy+shift;
    for(int h=-1; h<tSize-3; h+=4){
        for(int w=-1;w<tSize-3;w+=4){
            vec4 in1 = texelFetch(MainBuffer, (xy+ivec2(w, h)), 0);
            vec4 in2 = texelFetch(InputBuffer, (shifted+ivec2(w, h)), 0);
            //dist+= abs((in1.r+in1.g+in1.b+in1.a)
            //-(in2.r+in2.g+in2.b+in2.a));
            in1 = abs((in1)-(in2));
            dist+= in1.r+in1.g+in1.b+in1.a;
            /*    dist+= abs((texelFetch(MainBuffer, (xy+ivec2(w, h)), 0).x)
                -(texelFetch(InputBuffer, shifted+ivec2(w, h), 0).x));*/
                cnt+=4;
        }
    }
    return dist/float(cnt);
}
void main() {
    ivec2 xy = ivec2(gl_FragCoord.xy);
    xy+=ivec2(0,yOffset);
    ivec2 align = ivec2(0,0);
    int mpy = Mpy;
    int tSize = TILESIZE/mpy;
    ivec2 prevAlign = ivec2(texelFetch(AlignVectors, (xy), 0).xy);
    float lumDist = FLT_MAX;
    if(xy.x < maxSize.x && xy.y < maxSize.y && xy.x > minSize.x && xy.y > minSize.y)
    for(int h =-MAXY;h<MAXY;h++){
        for(int w = -MAXX;w<MAXX;w++){
            float inp = cmpTiles(xy*tSize,tSize,ivec2(w,h)+prevAlign/mpy);
            if(inp < lumDist){
                lumDist = inp;
                align = ivec2(w,h);
            }
        }
    }
    Output = vec2(prevAlign + mpy*align);
}
