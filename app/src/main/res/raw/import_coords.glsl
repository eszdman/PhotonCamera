ivec2 mirrorCoords(ivec2 xy, ivec4 bounds){
    if(xy.x < bounds.r){
        xy.x = bounds.r-xy.x;
    } else {
        if(xy.x > bounds.b){
            xy.x = bounds.b-(xy.x-bounds.b);
        }
    }
    if(xy.y < bounds.g){
        xy.y = bounds.g-xy.y;
    } else {
        if(xy.y > bounds.a){
            xy.y = bounds.a-(xy.y-bounds.a);
        }
    }
    return xy;
}