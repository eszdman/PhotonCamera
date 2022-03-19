vec4 mix4(vec4 inp[4],vec2 mixing){
    return mix(mix(inp[0],inp[1],mixing.x), mix(inp[2],inp[3],mixing.x), mixing.y);
}