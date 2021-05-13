ivec2 mirrorCoords(ivec2 xy, ivec4 bounds){
    if(xy.x < bounds.r){
        xy.x = (2*bounds.r-xy.x)%bounds.b;
    } else {
        if(xy.x > bounds.b){
            xy.x = 2*bounds.b-(xy.x%(bounds.b*2));
        }
    }
    if(xy.y < bounds.g){
        xy.y = (2*bounds.g-xy.y)%bounds.a;
    } else {
        if(xy.y > bounds.a){
            xy.y = 2*bounds.a-(xy.y%(bounds.a*2));
        }
    }
    return xy;
}
ivec2 mirrorCoords2(ivec2 xy, ivec2 bounds){

    /*if(xy.x < 0){
        xy.x = (-xy.x)%bounds.r;
    } else {
        if(xy.x > bounds.r){
            xy.x = 2*bounds.r-(xy.x%(bounds.r*2));
        }
    }
    if(xy.y < 0){
        xy.y = (-xy.y)%bounds.g;
    } else {
        if(xy.y > bounds.g){
            xy.y = 2*bounds.g-(xy.y%(bounds.g*2));
        }
    }*/
    xy%=bounds*2;
    if(xy.x < 0 || xy.x > bounds.x) xy.x = (bounds.x*2-xy.x)%bounds.x;
    if(xy.y < 0 || xy.y > bounds.y) xy.y = (bounds.y*2-xy.y)%bounds.y;
    return xy;
}