package com.particlesdevs.photoncamera.util;

import static androidx.core.content.ContextCompat.getSystemService;

import android.annotation.SuppressLint;
import android.content.Context;
import android.media.AudioDeviceInfo;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Build;

import androidx.core.content.ContextCompat;

import com.particlesdevs.photoncamera.app.PhotonCamera;
import com.particlesdevs.photoncamera.util.Log;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.util.Arrays;

/**
 * Records microphone audio and encodes it to an uncompressed FLAC file.
 * Uses AudioRecord with UNPROCESSED source for raw microphone data,
 * and technicallyflac (via JNI) for FLAC encoding.
 *
 * Output: verbatim (uncompressed) FLAC, 44100 Hz, stereo, 16-bit.
 *
 * Supports directing capture towards the front (user-facing) or rear
 * microphone via the API 29+ setPreferredMicrophoneDirection API.
 * Two instances can be started concurrently to record both mic locations
 * when the device supports simultaneous capture.
 */
public class FlacAudioRecorder {
    /** Receives raw PCM chunks; timestamps are System.nanoTime()-based. */
    public interface PcmSink { void onPcm(long timestampNs, short[] samples, int channels); }
    private static final String TAG = "FlacAudioRecorder";

    private static final int SAMPLE_RATE = 48000;

    // 6000 stereo frames = 24000 bytes per chunk, matching the chunk size of
    // genuine MediaCinemaRAW recordings (~125ms).
    private static final int BLOCK_SAMPLES = 6000;
    private static final int BIT_DEPTH = 16;

    static {
        System.loadLibrary("flacRecorder");
    }

    private native long nativeOpenFd(int fd, int sampleRate, int channels, int bitDepth, int blockSize);
    private native void nativeWriteFrame(long ctx, short[] samples, int frameCount, int channels);
    private native void nativeClose(long ctx);

    private AudioRecord audioRecord;
    private FileOutputStream outputFos;
    private Thread recordThread;
    private volatile boolean recording = false;
    private long nativeCtx = 0;
    private int actualChannels = 2;
    private volatile PcmSink pcmSink;

    public int getSampleRate() { return SAMPLE_RATE; }
    public int getChannels() { return actualChannels; }

    /**
     * Records raw microphone PCM and delivers it to the sink instead of
     * encoding a FLAC file. Used when the audio is embedded into a
     * MediaCinemaRAW container.
     */
    public synchronized boolean start(PcmSink sink) {
        if (recording || sink == null) return false;
        audioRecord = createAudioRecord();
        if (audioRecord == null || audioRecord.getState() != AudioRecord.STATE_INITIALIZED) {
            if (audioRecord != null) {
                audioRecord.release();
                audioRecord = null;
            }
            return false;
        }
        pcmSink = sink;
        recording = true;
        recordThread = new Thread(this::recordLoop, "MediaCinemaRAW-Audio");
        recordThread.setDaemon(true);
        recordThread.start();
        return true;
    }

    /**
     * Start recording to the given output path
     *
     * @param outputPath  Destination .flac file path
     * @return true if recording started successfully
     */
    public boolean start(String outputPath) {
        if (recording) {
            Log.w(TAG, "Already recording");
            return false;
        }

        audioRecord = createAudioRecord();
        if (audioRecord == null || audioRecord.getState() != AudioRecord.STATE_INITIALIZED) {
            Log.e(TAG, "AudioRecord init failed");
            if (audioRecord != null) {
                audioRecord.release();
                audioRecord = null;
            }
            return false;
        }

        // Open the output file from Java so it bypasses FUSE file-type restrictions
        // on Android 11+ (which block native fopen() for audio files in DCIM directories).
        // The raw fd from the Java FileOutputStream is passed to native for writing.
        int rawFd = openOutputFile(outputPath);
        if (rawFd < 0) {
            Log.e(TAG, "Cannot open output file: " + outputPath);
            audioRecord.release();
            audioRecord = null;
            return false;
        }

        nativeCtx = nativeOpenFd(rawFd, SAMPLE_RATE, actualChannels, BIT_DEPTH, BLOCK_SAMPLES);
        if (nativeCtx == 0) {
            Log.e(TAG, "FLAC encoder init failed");
            closeOutputFile();
            audioRecord.release();
            audioRecord = null;
            return false;
        }

        recording = true;
        String dirLabel = "MIC";
        recordThread = new Thread(this::recordLoop, "FlacAudioRecorder-" + dirLabel);
        recordThread.setDaemon(true);
        recordThread.start();
        Log.d(TAG, "Started [" + dirLabel + "]: " + outputPath
                + " (" + actualChannels + "ch, " + SAMPLE_RATE + "Hz)");
        return true;
    }

    /**
     * Stop recording and flush the FLAC file to disk.
     */
    public void stop() {
        if (!recording && nativeCtx == 0) return;

        recording = false;

        // Stop AudioRecord to unblock any pending read()
        if (audioRecord != null) {
            try { audioRecord.stop(); } catch (Exception ignored) {}
        }

        if (recordThread != null) {
            try { recordThread.join(3000); } catch (InterruptedException ignored) {}
            recordThread = null;
        }

        if (nativeCtx != 0) {
            nativeClose(nativeCtx);
            nativeCtx = 0;
        }

        // Close the Java-side file handle AFTER native has flushed and closed its dup'd fd
        closeOutputFile();

        if (audioRecord != null) {
            audioRecord.release();
            audioRecord = null;
        }

        Log.d(TAG, "Stopped");
    }

    /**
     * Opens the output file and returns a raw POSIX fd for native writing.
     *
     * Primary: {@link SimpleStorageHelper#openFdForWrite} via SAF ContentResolver —
     * bypasses FUSE MediaProvider type restrictions on Android 11+ (EPERM for audio in DCIM).
     *
     * Fallback: {@link FileOutputStream} + reflection (older APIs, app-specific paths).
     */
    private int openOutputFile(String path) {
        int fd = SimpleStorageHelper.openFdForWrite(path);
        if (fd >= 0) return fd;

        Log.w(TAG, "SAF open failed for " + path + ", falling back to FileOutputStream");
        try {
            File f = new File(path);
            if (f.getParentFile() != null) f.getParentFile().mkdirs();
            outputFos = new FileOutputStream(f);
            Field field = FileDescriptor.class.getDeclaredField("descriptor");
            field.setAccessible(true);
            return (int) field.get(outputFos.getFD());
        } catch (Exception e) {
            Log.e(TAG, "openOutputFile failed: " + e.getMessage());
            closeOutputFile();
            return -1;
        }
    }

    private void closeOutputFile() {
        if (outputFos != null) {
            try { outputFos.close(); } catch (Exception ignored) {}
            outputFos = null;
        }
    }

    private void recordLoop() {
        short[] buffer = new short[BLOCK_SAMPLES * actualChannels];
        try {
            audioRecord.startRecording();
        } catch (Exception e) {
            Log.e(TAG, "startRecording failed: " + e.getMessage());
            return;
        }

        while (recording) {
            int read = audioRecord.read(buffer, 0, buffer.length);
            if (read <= 0) break;
            if (nativeCtx != 0) {
                nativeWriteFrame(nativeCtx, buffer, read / actualChannels, actualChannels);
            } else if (pcmSink != null) {
                long durationNs = (long) (read / actualChannels) * 1_000_000_000L / SAMPLE_RATE;
                pcmSink.onPcm(System.nanoTime() - durationNs, java.util.Arrays.copyOf(buffer, read), actualChannels);
            }
        }

        Log.d(TAG, "Record loop ended");
        pcmSink = null;
    }

    /**
     * Tries audio sources and channel configs in priority order:
     * UNPROCESSED stereo → MIC stereo → MIC mono.
     * On API 29+, applies microphone direction preference after creation.
     */
    @SuppressWarnings("deprecation")
    private AudioRecord createAudioRecord() {
        int[] sources = {
            MediaRecorder.AudioSource.MIC,
            MediaRecorder.AudioSource.UNPROCESSED,
        };
        int[][] configs = {
            //{0b1111, 4, 0},
            //{0b111, 3, 0},
            {AudioFormat.CHANNEL_IN_STEREO, 2, 1},
            {AudioFormat.CHANNEL_IN_MONO,   1, 1}
        };

        for (int source : sources) {
            for (int[] config : configs) {
                int chanCfg = config[0];
                int numCh   = config[1];
                boolean legacy = config[2] == 1;
                int minBuf;
                if(legacy) {
                   minBuf = AudioRecord.getMinBufferSize(SAMPLE_RATE, chanCfg,
                            AudioFormat.ENCODING_PCM_16BIT) * numCh;
                } else {
                    minBuf = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO,
                            AudioFormat.ENCODING_PCM_16BIT);
                }
                if (minBuf <= 0) continue;
                int bufSize = Math.max(minBuf, BLOCK_SAMPLES * numCh * 8);
                try {
                    AudioFormat audioFormat = null;
                    if(legacy) {
                        audioFormat = new AudioFormat.Builder()
                                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                                .setSampleRate(SAMPLE_RATE)
                                .setChannelMask(chanCfg)
                                .build();
                    } else {
                        audioFormat = new AudioFormat.Builder()
                                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                                .setSampleRate(SAMPLE_RATE)
                                .setChannelIndexMask(chanCfg)
                                .build();
                    }
                    /*@SuppressLint("MissingPermission") AudioRecord rec = new AudioRecord(source, SAMPLE_RATE, chanCfg,
                            AudioFormat.ENCODING_PCM_16BIT, bufSize);*/
                    @SuppressLint("MissingPermission") AudioRecord rec = new AudioRecord.Builder()
                            .setAudioSource(source)
                            .setAudioFormat(audioFormat)
                            .setBufferSizeInBytes(bufSize)
                            .build();
                    if (rec.getState() != AudioRecord.STATE_INITIALIZED) {
                        rec.release();
                        continue;
                    }
                    // Apply microphone direction preference on API 29+
                    actualChannels = rec.getChannelCount();
                    Log.d(TAG, "AudioRecord: source=" + source + " ch=" + actualChannels);
                    return rec;
                } catch (Exception e) {
                    Log.d(TAG, Log.getStackTraceString(e));
                }
            }
        }
        return null;
    }
}
