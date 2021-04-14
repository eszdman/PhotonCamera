ivec2 mirrorCoords(ivec2 xy, ivec4 bounds){
    if(xy.x < bounds.r){
        xy.x = 2*bounds.r-xy.x;
    } else {
        if(xy.x > bounds.b){
            xy.x = 2*bounds.b-xy.x;
        }
    }
    if(xy.y < bounds.g){
        xy.y = 2*bounds.g-xy.y;
    } else {
        if(xy.y > bounds.a){
            xy.y = 2*bounds.a-xy.y;
        }
    }
    return xy;
}
ivec2 mirrorCoords2(ivec2 xy, ivec2 bounds){
    if(xy.x < 0){
        xy.x = -xy.x;
    } else {
        if(xy.x > bounds.r){
            xy.x = 2*bounds.r-xy.x;
        }
    }
    if(xy.y < 0){
        xy.y = -xy.y;
    } else {
        if(xy.y > bounds.g){
            xy.y = 2*bounds.g-xy.y;
        }
    }
    return xy;
}