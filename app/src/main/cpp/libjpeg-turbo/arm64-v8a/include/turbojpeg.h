#ifndef TURBOJPEG_H
#define TURBOJPEG_H

#include <stddef.h>

#ifdef __cplusplus
extern "C" {
#endif

typedef void* tjhandle;

#define TJSAMP_444 0
#define TJPF_RGB 0
#define TJFLAG_ACCURATEDCT 4096

tjhandle tjInitCompress(void);
int tjCompress2(tjhandle handle, const unsigned char *srcBuf, int width, int pitch, int height, int pixelFormat, unsigned char **jpegBuf, unsigned long *jpegSize, int jpegSubsamp, int jpegQual, int flags);
int tjFree(unsigned char *buffer);
int tjDestroy(tjhandle handle);
char* tjGetErrorStr2(tjhandle handle);

#ifdef __cplusplus
}
#endif

#endif
