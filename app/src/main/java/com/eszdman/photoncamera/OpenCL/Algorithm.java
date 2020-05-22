package com.eszdman.photoncamera.OpenCL;

import org.jocl.*;
import static org.jocl.CL.clReleaseMemObject;
import static org.jocl.CL.clSetKernelArg;

public class Algorithm {
    int inNum;
    String programSource;
    cl_kernel kernel;
    String name;
    Long start;
    public Algorithm(Program program){
        programSource = program.programSrc;
        inNum = program.inNum;
    }
    public void setArg(cl_mem[] memObjects) {
        start = System.currentTimeMillis();
        for (int i = 0; i < memObjects.length; i++) {
            clSetKernelArg(kernel, i,
                    Sizeof.cl_mem, Pointer.to(memObjects[i]));
        }
    }
    public void ReleaseAll(cl_mem[] memObjects) {
        System.out.println("Time elapsed: "+(System.currentTimeMillis()-start)+"ms "+ name);
        for (int i = 0; i < memObjects.length; i++) {
            clReleaseMemObject(memObjects[i]);
        }
    }
}
