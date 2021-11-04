

precision mediump float;

// Sigma 1.36
float gauss[25] = float[](
    0.01193f, 0.025908f, 0.033547f, 0.025908f, 0.01193f,
    0.025908f, 0.056266f, 0.072856f, 0.056266f, 0.025908f,
    0.033547f, 0.072856f, 0.094337f, 0.072856f, 0.033547f,
    0.025908f, 0.056266f, 0.072856f, 0.056266f, 0.025908f,
    0.01193f, 0.025908f, 0.033547f, 0.025908f, 0.01193f
);

uniform sampler2D frame;
uniform ivec2 bounds;

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
    ivec2 xy = ivec2(gl_FragCoord.xy) * 4;
    float val = 0.f;
    for (int i = 0; i < 25; i++) {
        ivec2 xyp = xy + ivec2((i % 5) - 2, (i / 5) - 2);
        xyp = mirrorOOBCoords(xyp);
        val += gauss[i] * texelFetch(frame, xyp, 0).x;
    }
    result = val;
}
