#version 300 es
uniform sampler2D InputBuffer;
uniform sampler2D DiffBuffer;

#define SIZE 0,0
out vec4 Output;

void main()
{
    ivec2 xy = ivec2(gl_FragCoord.xy);
    //vec2 uv = gl_FragCoord.xy/uResolution;
    vec3 maxrgb = vec3(0.0);
    vec3 minrgb = vec3(1.0);
    for(int i = -3;i<=3;i++){
        for(int j = -3;j<=3;j++){
            maxrgb = max(texelFetch(InputBuffer, xy+ivec2(i,j), 0).rgb,maxrgb);
            minrgb = min(texelFetch(InputBuffer, xy+ivec2(i,j), 0).rgb,minrgb);
        }
    }
    vec2 diffG = vec2(texelFetch(InputBuffer, xy+ivec2(1,0), 0).g-texelFetch(InputBuffer, xy-ivec2(1,0), 0).g,
    texelFetch(InputBuffer, xy+ivec2(0,1), 0).g-texelFetch(InputBuffer, xy-ivec2(0,1), 0).g);
    vec2 sigG = sign(diffG);
    vec3 inp = texelFetch(InputBuffer, xy, 0).rgb;
    vec4 diff = texelFetch(DiffBuffer, xy, 0).rgba;
    diff*=2.0;
    diff.rg/=maxrgb.r;
    diff.ba/=maxrgb.b;
    float temporalR = inp.r - (inp.g-minrgb.g)/(maxrgb.g-minrgb.g + 0.00001);
    float temporalB = inp.b  - (inp.g-minrgb.g)/(maxrgb.g-minrgb.g + 0.00001);
    vec2 xyr = vec2(abs(diff.rg*temporalR)*-sigG);
    vec2 xyb = vec2(abs(diff.ba*temporalB)*-sigG);
    float inpr = texture(InputBuffer, (gl_FragCoord.xy+vec2(xyr))/vec2(SIZE)).r;
    float inpb = texture(InputBuffer, (gl_FragCoord.xy+vec2(xyb))/vec2(SIZE)).b;
    Output.rgb = vec3(inpr,inp.g,inpb);
    Output.a = 1.0;
}