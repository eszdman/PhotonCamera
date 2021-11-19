vec4 hardThresholding(vec4 inp, float size){
    if(abs(inp.r) < size) inp.r = 0.0;
    if(abs(inp.g) < size) inp.g = 0.0;
    if(abs(inp.b) < size) inp.b = 0.0;
    if(abs(inp.a) < size) inp.a = 0.0;
    return inp;
}

float hardThresholding(float inp, float size){
    if(abs(inp) < size) return 0.0;
    return inp;
}
vec4 softThresholding(vec4 inp, float size){
    vec4 absed = abs(inp);
    inp/=absed+0.0001;
    absed = clamp(absed-size,0.0,1.0);
    return inp*(absed+0.0001);
}
vec4 softThresholding2(vec4 inp, float size){
    vec4 absed = abs(inp);
    inp/=absed+0.0001;
    absed = clamp(absed-size,0.0,1.0);
    return inp*(absed+0.0001)*(1.0+size);
}
vec4 softThresholding2(vec4 inp, vec4 size){
    vec4 absed = abs(inp);
    inp/=absed+0.0001;
    absed = clamp(absed-size,0.0,1.0);
    return inp*(absed+0.0001)*(vec4(1.0)+size);
}
float softThresholding(float inp, float size){
    if(abs(inp) < size) return 0.0;
    if(inp < 0.0) return inp+size;
    return inp-size;
}