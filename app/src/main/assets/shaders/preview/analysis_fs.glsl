#extension GL_OES_EGL_image_external_essl3 : require
precision mediump float;
uniform samplerExternalOES sTexture;
uniform bool mirror;
out vec4 Output;
in vec2 texCoord;
void main() {
    vec2 uv = texCoord.xy;
    if (mirror)
        uv.y = 1.0 - uv.y;
    Output = texture(sTexture, uv);
}
