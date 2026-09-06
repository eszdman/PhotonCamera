precision highp float;
precision highp sampler2D;
precision highp image2D;
uniform highp sampler2D InputTexture;
uniform vec3 blackLevel;
out vec3 Output;
void main() {
    ivec2 xy = ivec2(gl_FragCoord.xy);
    vec3 color = texelFetch(InputTexture, xy, 0).rgb;
    // Normalize the color values based on the black level
    color = max((color - blackLevel) / (vec3(1.0) - blackLevel), 0.0);
    // Write the normalized color to the output
    Output = color.rgb;
}