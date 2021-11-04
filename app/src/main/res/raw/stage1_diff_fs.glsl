

precision mediump float;

uniform sampler2D refFrame;
uniform ivec2 bounds;
uniform ivec2 direction;

out float result;

ivec2 mirrorOOBCoords(ivec2 coords) {
    ivec2 newCoords;

    if (coords.x < 0)
        newCoords.x = -coords.x;
    else if (coords.x >= bounds.x)
        newCoords.x = 2 * bounds.x - coords.x - 1;
    else
        newCoords.x = coords.x;

    if (coords.y < 0)
        newCoords.y = -coords.y;
    else if (coords.y >= bounds.y)
        newCoords.y = 2 * bounds.y - coords.y - 1;
    else
        newCoords.y = coords.y;

    return newCoords;
}

void main() {
    ivec2 xy = ivec2(gl_FragCoord.xy);
    result = texelFetch(refFrame, mirrorOOBCoords(xy + direction), 0).x
        - texelFetch(refFrame, mirrorOOBCoords(xy), 0).x;
}
