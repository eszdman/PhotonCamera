float[9] loadbayer9(sampler2D tex, ivec2 coords, int bayer){
    float outp[9];
    if(((coords.x+bayer%2)%2)+((coords.y+bayer/2)%2) == 1){
        outp[0] = float(texelFetch(tex, coords + ivec2(0,-2), 0).r);

        outp[1] = float(texelFetch(tex, coords + ivec2(-1,-1), 0).r);
        outp[2] = float(texelFetch(tex, coords + ivec2(1,-1), 0).r);

        outp[3] = float(texelFetch(tex, coords + ivec2(-2,0), 0).r);
        outp[4] = float(texelFetch(tex, coords + ivec2(0,0), 0).r);
        outp[5] = float(texelFetch(tex, coords + ivec2(2,0), 0).r);

        outp[6] = float(texelFetch(tex, coords + ivec2(-1,1), 0).r);
        outp[7] = float(texelFetch(tex, coords + ivec2(1,1), 0).r);

        outp[8] = float(texelFetch(tex, coords + ivec2(0,2), 0).r);
    } else {
        for (int x = -1; x <= 1; x++) {
            for (int y = -1; y <= 1; y++) {
                outp[(x + 1) * 3 + (y + 1)] = float(texelFetch(tex, coords + ivec2(x, y)*2, 0).r);
            }
        }
    }
    return outp;
}