#include <stddef.h>

typedef void* tjhandle;
extern "C" {
tjhandle tjInitCompress(void) { return (tjhandle)1; }
int tjCompress2(tjhandle handle, const unsigned char *srcBuf, int width, int pitch, int height, int pixelFormat, unsigned char **jpegBuf, unsigned long *jpegSize, int jpegSubsamp, int jpegQual, int flags) {
    *jpegBuf = (unsigned char*)1;
    *jpegSize = 0;
    return 0;
}
int tjFree(unsigned char *buffer) { return 0; }
int tjDestroy(tjhandle handle) { return 0; }
char* tjGetErrorStr2(tjhandle handle) { return (char*)""; }
}
