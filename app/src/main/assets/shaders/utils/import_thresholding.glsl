vec4 hardThresholding(vec4 inp, float size){
    //float mply = 1.0;
    /*
    if(abs(inp.r) < size) inp.r = 0.0;
    if(abs(inp.g) < size) inp.g = 0.0;
    if(abs(inp.b) < size) inp.b = 0.0;
    if(abs(inp.a) < size) inp.a = 0.0;
    */

    if(length(inp) < size) return vec4(0.0);
    return inp;
}

vec4 hardThresholding(vec4 inp, vec4 size){
    if(abs(inp.r) < size.r) inp.r = 0.0;
    if(abs(inp.g) < size.g) inp.g = 0.0;
    if(abs(inp.b) < size.b) inp.b = 0.0;
    if(abs(inp.a) < size.a) inp.a = 0.0;
    return inp;
}
float hardThresholding(float inp, float size){
    if(abs(inp) < size) return 0.0;
    return inp;
}
vec4 softThresholding(vec4 inp, float size){
    vec4 absed = abs(inp);
    absed = max(absed-size,0.0);
    return absed*sign(inp);
}
vec4 softThresholding(vec4 inp, vec4 size){
    vec4 absed = abs(inp);
    absed = max(absed-size,0.0);
    return absed*sign(inp);
}
vec4 softThresholding2(vec4 inp, float size){
    vec4 absed = abs(inp);
    absed = max(absed-size,0.0);
    return absed*sign(inp)*(1.0+size*2.0);
}

vec4 softThresholding2(vec4 inp, vec4 size){
    vec4 absed = abs(inp);
    absed = max(absed-size,0.0);
    return absed*sign(inp)*(1.0-size);
}

vec4 softestThresholding(vec4 inp, float size){
    float lent = length(inp);
    float prev = lent;
    inp/=lent;
    lent = max(lent-size,0.0);
    return inp*(lent+prev/10.0);
}
float softThresholding(float inp, float size){
    if(abs(inp) < size) return 0.0;
    if(inp < 0.0) return inp+size;
    return inp-size;
}