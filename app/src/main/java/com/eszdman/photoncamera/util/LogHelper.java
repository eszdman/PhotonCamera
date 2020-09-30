package com.eszdman.photoncamera.util;

import android.graphics.ImageFormat;

public class LogHelper {

    public static String getImageFormat(int format)
    {
        switch (format)
        {
            case ImageFormat.DEPTH16:
                return "DEPTH16:" + format;
            case ImageFormat.DEPTH_JPEG:
                return "DEPTH_JPEG:" + format;
            case ImageFormat.DEPTH_POINT_CLOUD:
                return "DEPTH_POINT_CLOUD:" + format;
            case ImageFormat.FLEX_RGB_888:
                return "FLEX_RGB_888:" + format;
            case ImageFormat.FLEX_RGBA_8888:
                return "FLEX_RGBA_8888:" + format;
            case ImageFormat.HEIC:
                return "HEIC:" + format;
            case ImageFormat.JPEG:
                return "JPEG:" + format;
            case ImageFormat.NV16:
                return "NV16:" + format;
            case ImageFormat.NV21:
                return "NV21:" + format;
            case ImageFormat.PRIVATE:
                return "PRIVATE:" + format;
            case ImageFormat.RAW10:
                return "RAW10:" + format;
            case ImageFormat.RAW12:
                return "RAW12:" + format;
            case ImageFormat.RAW_PRIVATE:
                return "RAW_PRIVATE:" + format;
            case ImageFormat.RAW_SENSOR:
                return "RAW_SENSOR:" + format;
            case ImageFormat.RGB_565:
                return "RGB_565:" + format;
            case ImageFormat.UNKNOWN:
                return "UNKNOWN:" + format;
            case ImageFormat.Y8:
                return "Y8:" + format;
            case ImageFormat.YUV_420_888:
                return "YUV_420_888:" + format;
            case ImageFormat.YUV_422_888:
                return "YUV_422_888:" + format;
            case ImageFormat.YUV_444_888:
                return "YUV_444_888:" + format;
            case ImageFormat.YUY2:
                return "YUY2:" + format;
            case ImageFormat.YV12:
                return "Yv12:" + format;
            default:
                return "Unknown Format with ID:" + format;
        }
    }
}
