

// Tiles of 16x16 with the middle 8x8 being the important region.
#define TILE_MIN_INDEX -4
#define TILE_MAX_INDEX 12

precision mediump float;

uniform sampler2D altFrame;
uniform ivec2 bounds;
uniform ivec2 direction;

out vec4 result;

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
    vec4 sum = vec4(0.f);
    // Use the same coordinate system as alignment stage.
    // Middle of the tile is 8x8, with 4 offset on all sides.
    for (int i = TILE_MIN_INDEX; i < TILE_MAX_INDEX; i++) {
        sum += texelFetch(altFrame, mirrorOOBCoords(xy + i * direction), 0);
    }
    result = sum;
}
