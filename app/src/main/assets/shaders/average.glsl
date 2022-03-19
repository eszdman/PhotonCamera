precision highp float;
precision highp usampler2D;
precision highp sampler2D;
uniform sampler2D InputBuffer;
uniform usampler2D InputBuffer2;
uniform int unlimitedcount;
uniform vec4 blackLevel;
uniform int CfaPattern;
uniform vec3 WhitePoint;
uniform int whitelevel;
uniform int first;
uniform int yOffset;
out float Output;

void main() {
    ivec2 xy = ivec2(gl_FragCoord.xy);
    xy+=ivec2(0,yOffset);
    ivec2 fact = (xy-ivec2(CfaPattern%2,CfaPattern/2))%2;
    float balance;
    if(fact.x+fact.y == 1){
        balance = WhitePoint.g;
    } else {
        if(fact.x == 0){
            balance = WhitePoint.r;
        } else {
            balance = WhitePoint.b;
        }
    }
    if(first == 1){
        Output =
        clamp(float(texelFetch(InputBuffer2, (xy), 0).x)/float(whitelevel),0.0,balance);
    } else {
        Output =
        mix(
        float(texelFetch(InputBuffer, (xy), 0).x)
        ,
        clamp(float(texelFetch(InputBuffer2, (xy), 0).x)/float(whitelevel),0.0,balance)
        ,
        1.f/float(unlimitedcount)
        );
    }
    //Output = clamp(Output,0.0,balance);
}