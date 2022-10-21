ivec2 rescalei(ivec2 inp, ivec4 required,ivec2 size){
    ivec2 size2 = ivec2(required.ba-required.rg);
    return min(max(inp*size2/size + required.rg,required.rg),required.ba);
}
ivec2 rescaleUpi(ivec2 inp, ivec4 required,int scaling){
    return min(max(inp*scaling + required.rg,required.rg),required.ba);
}
ivec2 rescaleDowni(ivec2 inp, ivec4 required,int scaling){
    return min(max(inp/scaling + required.rg,required.rg),required.ba);
}

ivec2 rescale(ivec2 inp, ivec4 required,ivec2 size){
    vec2 flt = vec2(max(min(inp,size),ivec2(0)))/vec2(size-1);
    return ivec2(mix(vec2(0.0),vec2(required.ba-required.rg-1),flt))+required.rg;
}
ivec2 rescale(ivec2 inp, ivec2 required,ivec2 size){
    vec2 flt = vec2(max(min(inp,size-1),ivec2(0)))/vec2(size-1);
    return ivec2(mix(vec2(0.0),vec2(required-1),flt));
}
