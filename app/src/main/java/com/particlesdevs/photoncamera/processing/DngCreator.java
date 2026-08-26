package com.particlesdevs.photoncamera.processing;

import android.media.Image;
import android.os.Build;

import com.particlesdevs.photoncamera.processing.render.Parameters;

import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import de.hdodenhof.circleimageview.BuildConfig;

public class DngCreator {
    private long nativePtr;
    private static final String TAG = "DngCreator";

    // CFA Pattern constants
    public static final int CFA_PATTERN_RGGB = 0;
    public static final int CFA_PATTERN_GRBG = 1;
    public static final int CFA_PATTERN_GBRG = 2;
    public static final int CFA_PATTERN_BGGR = 3;

    // Native methods
    private native long create();
    private native ByteBuffer createDNG(long nativePtr, int width, int height, ByteBuffer rawImageData);
    private native void setOrientation(long nativePtr, int orientation);
    private native void setWhiteLevel(long nativePtr, double whiteLevel);
    private native void setBlackLevel(long nativePtr, short[] blackLevel);
    private native void setColorMatrix1(long nativePtr, double[] matrix);
    private native void setColorMatrix2(long nativePtr, double[] matrix);
    private native void setForwardMatrix1(long nativePtr, double[] matrix);
    private native void setForwardMatrix2(long nativePtr, double[] matrix);
    private native void setCameraCalibration1(long nativePtr, double[] matrix);
    private native void setCameraCalibration2(long nativePtr, double[] matrix);
    private native void setAsShotNeutral(long nativePtr, double[] neutral);
    private native void setAsShotWhiteXY(long nativePtr, double x, double y);
    private native void setAnalogBalance(long nativePtr, double[] balance);
    private native void setCalibrationIlluminant1(long nativePtr, short illuminant);
    private native void setCalibrationIlluminant2(long nativePtr, short illuminant);
    private native void setUniqueCameraModel(long nativePtr, String model);
    private native void setDescription(long nativePtr, String desc);
    private native void setSoftware(long nativePtr, String soft);
    private native void setIso(long nativePtr, short iso);
    private native void setExposureTime(long nativePtr, double exposureTime);
    private native void setCFAPattern(long nativePtr, int pattern);
    private native void setGainMap(long nativePtr, float[] gainMap, int xmin, int ymin, int xmax, int ymax, int width, int height);
    private native void setAperture(long nativePtr, double aperture);
    private native void setFocalLength(long nativePtr, double focalLength);
    private native void setMake(long nativePtr, String make);
    private native void setModel(long nativePtr, String model);
    private native void setTimeCode(long nativePtr, byte[] timecode);
    private native void setDateTime(long nativePtr, String datetime);
    private native void setNoiseProfile(long nativePtr, double[] noiseProfile);
    private native void setFrameRate(long nativePtr, double frameRate);
    private native void setCompression(long nativePtr, boolean useCompression);
    private native void setBitsPerSample(long nativePtr, int bps);
    private native void setBinning(long nativePtr, boolean binning);
    private native void destroy(long nativePtr);
    private native void writeFile(long nativePtr, ByteBuffer dngBuffer, ByteBuffer raw, String path, int offset);
    private native void openArchive(long nativePtr, String path);
    private native void openArchiveByFd(long nativePtr, int fd);
    private native void closeArchive(long nativePtr);

    public DngCreator() {
        nativePtr = create();
        if (nativePtr == 0) {
            throw new RuntimeException("Failed to create DngCreator");
        }
    }

    /**
     * Set the orientation of the DNG image
     * @param orientation The orientation enum
     */
    public void setOrientation(int orientation) {
        if (orientation < 0 || orientation > 8) {
            throw new IllegalArgumentException("Invalid orientation value. Must be between 0 and 8.");
        }
        int rot = 0;
        switch (orientation){
            case 0: // 0 degrees
                rot = 1;
                break;
            case 1: // 90 degrees
                rot = 6;
                break;
            case 2: // 180 degrees
                rot = 3;
                break;
            case 3: // 270 degrees
                rot = 8;
                break;
        }
        setOrientation(nativePtr, rot);
    }

    /**
     * Set the white level for the DNG image
     * @param whiteLevel The white level value (typically 65535 for 16-bit)
     */
    public void setWhiteLevel(double whiteLevel) {
        setWhiteLevel(nativePtr, whiteLevel);
    }

    /**
     * Set the black level for the DNG image
     * @param blackLevel Array of 4 black level values for RGGB pattern
     */
    public void setBlackLevel(short[] blackLevel) {
        if (blackLevel.length != 4) {
            throw new IllegalArgumentException("Black level array must have 4 elements");
        }
        setBlackLevel(nativePtr, blackLevel);
    }

    /**
     * Set the color matrix 1 (for first illuminant)
     * @param matrix 3x3 color matrix as 9-element array
     */
    public void setColorMatrix1(double[] matrix) {
        if (matrix.length != 9) {
            throw new IllegalArgumentException("Color matrix must have 9 elements");
        }
        setColorMatrix1(nativePtr, matrix);
    }

    /**
     * Set the color matrix 2 (for second illuminant)
     * @param matrix 3x3 color matrix as 9-element array
     */
    public void setColorMatrix2(double[] matrix) {
        if (matrix.length != 9) {
            throw new IllegalArgumentException("Color matrix must have 9 elements");
        }
        setColorMatrix2(nativePtr, matrix);
    }

    /**
     * Set the forward matrix 1
     * @param matrix 3x3 forward matrix as 9-element array
     */
    public void setForwardMatrix1(double[] matrix) {
        if (matrix.length != 9) {
            throw new IllegalArgumentException("Forward matrix must have 9 elements");
        }
        setForwardMatrix1(nativePtr, matrix);
    }

    /**
     * Set the forward matrix 2
     * @param matrix 3x3 forward matrix as 9-element array
     */
    public void setForwardMatrix2(double[] matrix) {
        if (matrix.length != 9) {
            throw new IllegalArgumentException("Forward matrix must have 9 elements");
        }
        setForwardMatrix2(nativePtr, matrix);
    }

    /**
     * Set the camera calibration matrix 1
     * @param matrix 3x3 camera calibration matrix as 9-element array
     */
    public void setCameraCalibration1(double[] matrix) {
        if (matrix.length != 9) {
            throw new IllegalArgumentException("Camera calibration matrix must have 9 elements");
        }
        setCameraCalibration1(nativePtr, matrix);
    }

    /**
     * Set the camera calibration matrix 2
     * @param matrix 3x3 camera calibration matrix as 9-element array
     */
    public void setCameraCalibration2(double[] matrix) {
        if (matrix.length != 9) {
            throw new IllegalArgumentException("Camera calibration matrix must have 9 elements");
        }
        setCameraCalibration2(nativePtr, matrix);
    }

    /**
     * Set the as-shot neutral values
     * @param neutral Array of 3 neutral values for RGB
     */
    public void setAsShotNeutral(double[] neutral) {
        if (neutral.length != 3) {
            throw new IllegalArgumentException("As-shot neutral array must have 3 elements");
        }
        setAsShotNeutral(nativePtr, neutral);
    }

    /**
     * Set the as-shot white point coordinates
     * @param x X coordinate
     * @param y Y coordinate
     */
    public void setAsShotWhiteXY(double x, double y) {
        setAsShotWhiteXY(nativePtr, x, y);
    }

    /**
     * Set the analog balance values
     * @param balance Array of 3 analog balance values for RGB
     */
    public void setAnalogBalance(double[] balance) {
        if (balance.length != 3) {
            throw new IllegalArgumentException("Analog balance array must have 3 elements");
        }
        setAnalogBalance(nativePtr, balance);
    }

    /**
     * Set the calibration illuminant 1
     * @param illuminant Illuminant type (e.g., 17 for Standard illuminant A, 21 for D65)
     */
    public void setCalibrationIlluminant1(short illuminant) {
        setCalibrationIlluminant1(nativePtr, illuminant);
    }

    /**
     * Set the calibration illuminant 2
     * @param illuminant Illuminant type (e.g., 17 for Standard illuminant A, 21 for D65)
     */
    public void setCalibrationIlluminant2(short illuminant) {
        setCalibrationIlluminant2(nativePtr, illuminant);
    }

    /**
     * Set the unique camera model name
     * @param model Camera model string
     */
    public void setUniqueCameraModel(String model) {
        setUniqueCameraModel(nativePtr, model);
    }

    /**
     * Set the unique camera model name
     * @param desc Camera description string
     */
    public void setDescription(String desc) {
        setDescription(nativePtr, desc);
    }

    /**
     * Set the unique camera model name
     * @param software Camera software string
     */
    public void setSoftware(String software) {
        setSoftware(nativePtr, software);
    }

    /**
     * Set the ISO sensitivity value
     * @param iso ISO value (e.g., 100, 200, 400, 800, etc.)
     */
    public void setIso(short iso) {
        setIso(nativePtr, iso);
    }

    /**
     * Set the exposure time in seconds
     * @param exposureTime Exposure time in seconds (e.g., 0.033 for 1/30s)
     */
    public void setExposureTime(double exposureTime) {
        setExposureTime(nativePtr, exposureTime);
    }

    /**
     * Set the aperture f-number
     * @param aperture Aperture f-number (e.g., 1.8, 2.8, 5.6, etc.)
     */
    public void setAperture(double aperture) {
        setAperture(nativePtr, aperture);
    }

    /**
     * Set the focal length in millimeters
     * @param focalLength Focal length in millimeters (e.g., 24.0, 50.0, 85.0, etc.)
     */
    public void setFocalLength(double focalLength) {
        setFocalLength(nativePtr, focalLength);
    }

    /**
     * Set the camera manufacturer name
     * @param make Camera manufacturer name (e.g., "Canon", "Nikon", "Sony", etc.)
     */
    public void setMake(String make) {
        setMake(nativePtr, make);
    }

    /**
     * Set the camera model name
     * @param model Camera model name (e.g., "EOS R5", "D850", "A7R IV", etc.)
     */
    public void setModel(String model) {
        setModel(nativePtr, model);
    }

    /**
     * Set the timecode information (8 bytes)
     * @param timecode Timecode as 8-byte array (typically for video/cinema DNG)
     */
    public void setTimeCode(byte[] timecode) {
        if (timecode.length != 8) {
            throw new IllegalArgumentException("Timecode array must have exactly 8 elements");
        }
        setTimeCode(nativePtr, timecode);
    }

    /**
     * Set the date and time when the image was captured
     * @param datetime Date and time in format "YYYY:MM:DD HH:MM:SS" (e.g., "2023:12:25 14:30:45")
     */
    public void setDateTime(String datetime) {
        if (datetime == null || datetime.length() != 19) {
            throw new IllegalArgumentException("DateTime must be in format \"YYYY:MM:DD HH:MM:SS\" (19 characters)");
        }
        // Basic format validation
        if (datetime.charAt(4) != ':' || datetime.charAt(7) != ':' || datetime.charAt(10) != ' ' ||
            datetime.charAt(13) != ':' || datetime.charAt(16) != ':') {
            throw new IllegalArgumentException("DateTime format invalid. Expected \"YYYY:MM:DD HH:MM:SS\"");
        }
        setDateTime(nativePtr, datetime);
    }

    /**
     * Set the noise profile for the image
     * @param noiseProfile Array containing noise profile data as pairs of (scale, offset) values for each color plane.
     *                     For RGB: [R_scale, R_offset, G_scale, G_offset, B_scale, B_offset]
     *                     For Bayer CFA: [R_scale, R_offset, G_scale, G_offset, B_scale, B_offset] (6 values total)
     */
    public void setNoiseProfile(double[] noiseProfile) {
        if (noiseProfile == null || noiseProfile.length == 0) {
            throw new IllegalArgumentException("Noise profile array cannot be null or empty");
        }
        if (noiseProfile.length % 2 != 0) {
            throw new IllegalArgumentException("Noise profile array must contain pairs of (scale, offset) values");
        }
        if (noiseProfile.length > 8) {
            throw new IllegalArgumentException("Noise profile array cannot exceed 8 elements (4 color planes max)");
        }
        setNoiseProfile(nativePtr, noiseProfile);
    }

    /**
     * Set the frame rate for video/cinema DNG sequences
     * @param frameRate Frames per second (e.g., 24.0, 25.0, 29.97, 30.0)
     */
    public void setFrameRate(double frameRate) {
        if (frameRate <= 0.0) {
            throw new IllegalArgumentException("Frame rate must be positive");
        }
        setFrameRate(nativePtr, frameRate);
    }

    /**
     * Set the compression type for the DNG image
     * @param useCompression true to use compression, false to store uncompressed
     */
    public void setCompression(boolean useCompression) {
        setCompression(nativePtr, useCompression);
    }

    /**
     * Set the bps value for the DNG image
     * @param bps 16 to use full range
     */
    public void setBitsPerSample(int bps) {
        setBitsPerSample(nativePtr, bps);
    }

    /**
     * Enable or disable Bayer 2×2 binning before saving.
     *
     * When enabled, same-color pixels from each 4×4 input block are combined into
     * one output pixel, halving both dimensions.
     *
     * Summing mode (white level < 65535): the four same-color values are summed,
     * so the effective white level and black levels are multiplied by 4.
     *
     * Averaging mode (white level ≥ 65535, i.e. already 16-bit full-range): the four
     * values are averaged, keeping levels unchanged and preventing overflow.
     *
     * @param binning true to apply Bayer binning, false for normal output
     */
    public void setBinning(boolean binning) {
        setBinning(nativePtr, binning);
    }

    /**
     * Set the CFA (Color Filter Array) pattern
     * @param pattern CFA pattern type (use CFA_PATTERN_* constants)
     */
    public void setCFAPattern(int pattern) {
        if (pattern < CFA_PATTERN_RGGB || pattern > CFA_PATTERN_BGGR) {
            throw new IllegalArgumentException("Invalid CFA pattern. Use CFA_PATTERN_* constants.");
        }
        setCFAPattern(nativePtr, pattern);
    }

    public void setGainMap(float[] gainMap, int xmin, int ymin, int xmax, int ymax, int width, int height) {
        if (gainMap.length < 4) {
            throw new IllegalArgumentException("Gain map must have 4 elements");
        }
        setGainMap(nativePtr, gainMap, xmin, ymin, xmax, ymax, width, height);
    }

    public void writeImage(OutputStream outputStream, Image image) {
        ByteBuffer rawImageData = image.getPlanes()[0].getBuffer();
        int width = image.getWidth();
        int height = image.getHeight();
        writeBuffer(outputStream, rawImageData, width, height);
    }

    public ByteBuffer dngBuffer(ByteBuffer buffer, int width, int height) {
        ByteBuffer dngData = createDNG(nativePtr, width, height, buffer);
        return dngData;
    }

    public void writeBuffer(OutputStream outputStream, ByteBuffer buffer, int width, int height) {
        ByteBuffer dngData = createDNG(nativePtr, width, height, buffer);
        if (dngData == null) {
            throw new RuntimeException("Failed to create DNG data");
        }

        // Stream directly out of the native buffer: a full-size heap copy of
        // the DNG (~128 MB at 64 MP) would spike memory right after merging.
        byte[] chunk = new byte[256 * 1024];
        try {
            while (dngData.hasRemaining()) {
                int n = Math.min(chunk.length, dngData.remaining());
                dngData.get(chunk, 0, n);
                outputStream.write(chunk, 0, n);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to write DNG data to output stream", e);
        }
    }

    public void writeFile(ByteBuffer dngBuffer, ByteBuffer raw, String path, int offset) {
        writeFile(nativePtr, dngBuffer, raw, path, offset);
    }

    double[] toDouble(float[] array) {
        double[] result = new double[array.length];
        for (int i = 0; i < array.length; i++) {
            result[i] = array[i];
        }
        return result;
    }

    public void setParameters(Parameters parameters) {
        short[] blackLevel = new short[4];
        for (int i = 0; i < 4; i++) {
            blackLevel[i] = (short) parameters.blackLevel[i];
        }
        setDescription(parameters.toString());
        setSoftware("PhotonCamera v" + BuildConfig.VERSION_NAME+BuildConfig.VERSION_CODE);
        
        // Set current date and time
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US);
        setDateTime(dateFormat.format(new Date()));
        
        // Set noise profile from noise modeler
        if (parameters.noiseModeler != null && parameters.noiseModeler.baseModel != null) {
            double[] noiseProfile = new double[6]; // RGB: scale, offset pairs
            // R channel
            noiseProfile[0] = parameters.noiseModeler.computeModel[0].first;  // R scale
            noiseProfile[1] = parameters.noiseModeler.computeModel[0].second; // R offset
            // G0 channel
            noiseProfile[2] = parameters.noiseModeler.computeModel[1].first;  // G scale
            noiseProfile[3] = parameters.noiseModeler.computeModel[1].second; // G offset

            // B channel
            noiseProfile[4] = parameters.noiseModeler.computeModel[2].first;  // B scale
            noiseProfile[5] = parameters.noiseModeler.computeModel[2].second; // B offset

            setNoiseProfile(noiseProfile);
        }
        
        setMake(Build.BRAND != null ? Build.BRAND : Build.MANUFACTURER);
        setModel(Build.MODEL);
        setUniqueCameraModel(Build.MODEL + "-" + Build.BRAND + "-" + Build.MANUFACTURER);
        setIso((short) parameters.iso);
        setExposureTime(parameters.exposureTime);
        setFocalLength(parameters.focalLength);
        setAperture(parameters.aperture);
        setBlackLevel(blackLevel);
        setWhiteLevel(parameters.whiteLevel);
        setCalibrationIlluminant1((short) parameters.calibrationIlluminant1);
        setCalibrationIlluminant2((short) parameters.calibrationIlluminant2);
        setColorMatrix1(toDouble(parameters.ColorMatrix1));
        setColorMatrix2(toDouble(parameters.ColorMatrix2));
        setForwardMatrix1(toDouble(parameters.ForwardTransform1));
        setForwardMatrix2(toDouble(parameters.ForwardTransform2));
        setCameraCalibration1(toDouble(parameters.calibrationTransform1));
        setCameraCalibration2(toDouble(parameters.calibrationTransform2));
        setAsShotNeutral(toDouble(parameters.whitePoint));
        setCFAPattern(parameters.cfaPattern);
        setOrientation(parameters.cameraRotation/90);
        setGainMap(parameters.gainMap,
                   parameters.sensorPix.top,
                   parameters.sensorPix.left,
                   parameters.sensorPix.bottom,
                   parameters.sensorPix.right,
                   parameters.mapSize.x,
                   parameters.mapSize.y);
    }

    /**
     * Open a ZIP archive for writing.  Subsequent calls to {@link #writeFile} will store each DNG
     * as an uncompressed entry inside this archive instead of writing individual files.  Call
     * {@link #closeArchive} when all frames have been written to finalise the ZIP.
     *
     * @param archivePath Absolute path of the output .zip file (must be writable)
     */
    public void openArchive(String archivePath) {
        if (archivePath == null || archivePath.isEmpty()) {
            throw new IllegalArgumentException("archivePath must not be null or empty");
        }
        openArchive(nativePtr, archivePath);
    }

    /**
     * Open an archive for writing via an Android SAF file descriptor.  The file descriptor must
     * be writable and remain open until {@link #closeArchive} is called.  Subsequent
     * {@link #writeFile} calls will store each DNG as an archive entry instead of individual files.
     *
     * @param fd Writable file descriptor obtained from {@code ParcelFileDescriptor.getFd()}
     */
    public void openArchiveByFd(int fd) {
        if (fd < 0) throw new IllegalArgumentException("fd must be a valid (>= 0) file descriptor");
        openArchiveByFd(nativePtr, fd);
    }

    /**
     * Finalise and close the currently open archive.  Flushes all buffered data and the archive
     * trailer, then closes the underlying file/fd.  Safe to call even if no archive is open
     * (no-op in that case).
     */
    public void closeArchive() {
        closeArchive(nativePtr);
    }

    /**
     * Clean up native resources
     */
    public void close() {
        if (nativePtr != 0) {
            destroy(nativePtr);
            nativePtr = 0;
        }
    }

    @Override
    protected void finalize() throws Throwable {
        try {
            close();
        } finally {
            super.finalize();
        }
    }

    static {
        System.loadLibrary("dngCreator");
    }


}
