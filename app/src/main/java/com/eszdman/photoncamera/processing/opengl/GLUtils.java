package com.eszdman.photoncamera.processing.opengl;

public class GLUtils {
    private final GLProg glProg;
    private GLCoreBlockProcessing glProcessing;

    public GLUtils(GLCoreBlockProcessing blockProcessing) {
        glProg = blockProcessing.mProgram;
        glProcessing = blockProcessing;
    }

    public GLTexture blur(GLTexture in, int size) {
        glProg.useProgram("#version 300 es\n" +
                "#define tvar " + in.mFormat.getTemVar() + "\n" +
                "#define tscal " + in.mFormat.getScalar() + "\n" +
                "precision mediump float;\n" +
                "precision mediump " + in.mFormat.getTemSamp() + ";\n" +
                "uniform sampler2D InputBuffer;\n" +
                "uniform int yOffset;\n" +
                "out float Output;\n" +
                "#define size1 " + ((double) (size) * 0.4) + "\n" +
                "#define MSIZE1 " + size + "\n" +
                "float normpdf(in float x, in float sigma){return 0.39894*exp(-0.5*x*x/(sigma*sigma))/sigma;}\n" +
                "void main() {\n" +
                "    ivec2 xy = ivec2(gl_FragCoord.xy);\n" +
                "    xy+=ivec2(0,yOffset);\n" +
                "    const int kSize = (MSIZE1-1)/2;\n" +
                "    float kernel[MSIZE1];\n" +
                "    float mask = 0.0;\n" +
                "    float pdfsize = 0.0;\n" +
                "    for (int j = 0; j <= kSize; ++j) kernel[kSize+j] = kernel[kSize-j] = normpdf(float(j), size1);\n" +
                "    for (int i=-kSize; i <= kSize; ++i){\n" +
                "        for (int j=-kSize; j <= kSize; ++j){\n" +
                "            float pdf = kernel[kSize+j]*kernel[kSize+i];\n" +
                "            mask+=float(texelFetch(InputBuffer, (xy+ivec2(i,j)), 0).x)*pdf;\n" +
                "            pdfsize+=pdf;\n" +
                "        }\n" +
                "    }\n" +
                "    mask/=pdfsize;\n" +
                "    Output = mask;\n" +
                "}\n");
        glProg.setTexture("InputBuffer", in);
        GLTexture out = new GLTexture(in);
        glProg.drawBlocks(out);
        glProg.close();
        return out;
    }
}
