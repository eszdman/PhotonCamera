//        UP 0
//LEFT 1 CENTER 2 RIGHT 3
//        DOWN 4
vec4 mix5(vec4 inp[5],vec2 mixing){
    vec4 mixX;
    vec4 mixY;
    if(mixing.x > 0.0){
    mixX = mix(inp[2],inp[3],mixing.x);
    } else {
        mixX = mix(inp[2],inp[1],-mixing.x);
    }
    if(mixing.y > 0.0){
        mixY = mix(inp[2],inp[0],mixing.x);
    } else {
        mixY = mix(inp[2],inp[4],-mixing.x);
    }
    return (mixX+mixY)/2.0;
}