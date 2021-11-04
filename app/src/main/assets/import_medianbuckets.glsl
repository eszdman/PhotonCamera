#define SORTCOUNT 12
float median1(sampler2D imageIn,ivec2 xy,ivec2 size) {
    vec2 sorting[SORTCOUNT];
    vec2 minmax;
    minmax.r = 1.0;
    for(int i =-size.x;i<=size.x;i++){
        for(int j =-size.x;j<=size.x;j++){
            float brInp = texelFetch(imageIn,xy+ivec2(i,j)).r;
            minmax.r = min(minmax.r,brInp);
            minmax.g = max(minmax.g,brInp);
        }
    }
    ivec3 cnt = ivec3(0);
    for(int i =-size.x;i<=size.x;i++){
        for(int j =-size.x;j<=size.x;j++){
            float brInp = texelFetch(imageIn,xy+ivec2(i,j)).r;
            int coord = int(brInp*float(SORTCOUNT));
            sorting[coords]+=vec2(brInp,1.0);
        }
    }
    return sorting[SORTCOUNT/2].r/sorting[SORTCOUNT/2].g;
}