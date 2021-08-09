vec2[9] bayer9(ivec2 xy, sampler2D inTex){
    vec2 v[9];
    ivec2 fact = (xy)%2;
    if(fact.x+fact.y == 1){
        v[4].r = float(texelFetch(inTex, xy + ivec2(0, 0), 0).r);
        v[4].g = 0.0;
        v[1].r = float(texelFetch(inTex, xy + ivec2(1, 1), 0).r);
        v[1].g = 1.4;
        v[2].r = float(texelFetch(inTex, xy + ivec2(-1, -1), 0).r);
        v[2].g = 1.4;
        v[3].r = float(texelFetch(inTex, xy + ivec2(1, -1), 0).r);
        v[3].g = 1.4;
        v[0].r = float(texelFetch(inTex, xy + ivec2(-1, 1), 0).r);
        v[0].g = 1.4;
        v[5].r = float(texelFetch(inTex, xy + ivec2(0, 2), 0).r);
        v[5].g = 2.0;
        v[6].r = float(texelFetch(inTex, xy + ivec2(2, 0), 0).r);
        v[6].g = 2.0;
        v[7].r = float(texelFetch(inTex, xy + ivec2(0, -2), 0).r);
        v[7].g = 2.0;
        v[8].r = float(texelFetch(inTex, xy + ivec2(-2, 0), 0).r);
        v[8].g = 2.0;
    } else {
        for (int dX = -1; dX <= 1; ++dX) {
            for (int dY = -1; dY <= 1; ++dY) {
                ivec2 offset = ivec2((dX), (dY));
                v[(dX + 1) * 3 + (dY + 1)].r = float(texelFetch(inTex, xy + offset*2, 0).r);
            }
        }
        v[0].g = 2.8;v[3].g = 2.0;v[6].g = 2.8;
        v[1].g = 2.0;v[4].g = 0.0;v[7].g = 2.0;
        v[2].g = 2.8;v[5].g = 2.0;v[8].g = 2.8;
    }
    return v;
}
