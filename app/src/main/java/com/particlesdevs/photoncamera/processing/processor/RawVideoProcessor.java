package com.particlesdevs.photoncamera.processing.processor;

import android.annotation.SuppressLint;
import android.graphics.ImageFormat;
import android.graphics.Point;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.media.Image;
import android.os.StatFs;

import com.particlesdevs.photoncamera.api.ParseExif;
import com.particlesdevs.photoncamera.app.PhotonCamera;
import com.particlesdevs.photoncamera.control.Gyro;
import com.particlesdevs.photoncamera.processing.DngCreator;
import com.particlesdevs.photoncamera.processing.ImageSaver;
import com.particlesdevs.photoncamera.processing.ProcessingEventsListener;
import com.particlesdevs.photoncamera.settings.PreferenceKeys;
import com.particlesdevs.photoncamera.util.Allocator;
import com.particlesdevs.photoncamera.util.FlacAudioRecorder;
import com.particlesdevs.photoncamera.util.Log;
import com.particlesdevs.photoncamera.util.McrawWriter;
import com.particlesdevs.photoncamera.util.SimpleStorageHelper;
import com.particlesdevs.photoncamera.processing.render.Parameters;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class RawVideoProcessor extends ProcessorBase {
    private static final String TAG = "RawVideoProcessor";
    public static int videoCounter = 1;

    private final FlacAudioRecorder rawAudioRecorder = new FlacAudioRecorder();

    private volatile boolean fillParams = false;
    private Path outputFolder;
    private int writeBufferSize = 16;
    private int writeBufferCounter = 0;
    private final AtomicInteger pendingWrites = new AtomicInteger(0);
    private volatile ByteBuffer[] dngBuffers = null;
    private volatile ByteBuffer[] rawBuffers = null;
    private DngCreator dngCreator = null;
    private ExecutorService writeExecutor = null;
    private boolean isRecording = false;

    private long recordingStartMs = 0;    private long availableBytesAtStart = 0;
    private int frameWidth = 0;
    private int frameHeight = 0;
    private static final int BITS_PER_SAMPLE = 10;
    private Parameters parameters;

    // --- MediaCinemaRAW (.mcraw) state ---
    // Atomic lifecycle. The container is created exactly once per recording,
    // asynchronously on the writer thread: videoStart only schedules the open
    // and returns immediately, so the camera handler thread stays free to
    // CLOSE incoming Images while SAF file creation (hundreds of ms) runs.
    // The ImageReader queue therefore never fills and the producer never
    // stalls, even at 60fps. INITIALIZING -> OPENED (container ready) ->
    // READY (geometry resolved on the first post-open frame); FAILED if the
    // container could not be created; ENDED after videoEnd, and late frames
    // after stop land in ENDED and are simply closed.
    private static final int MCRAW_INITIALIZING = 0;
    private static final int MCRAW_OPENED = 1;
    private static final int MCRAW_READY = 2;
    private static final int MCRAW_FAILED = 3;
    private static final int MCRAW_ENDED = 4;
    // Two-stage frame pipeline: parallel encoders read the camera Image in
    // place and release it as soon as encoding finishes (before any disk IO),
    // while a single ordered writer appends the encoded slots to the file.
    // The slot pool is the write-latency shock absorber: admission pauses
    // only when every slot is awaiting disk, so the depth must cover FUSE
    // stalls (cold-start writes can take ~80ms; warm spikes 30-50ms).
    private static final int MCRAW_ENCODER_THREADS = 2;
    private static final int MCRAW_SLOT_COUNT = 6;
    private static final String[] MCRAW_CFA_NAMES = {"rggb", "grbg", "gbrg", "bggr"};
    private final AtomicInteger mcrawState = new AtomicInteger(MCRAW_ENDED);
    private final AtomicInteger mcrawHeldImages = new AtomicInteger(0);
    private final java.util.concurrent.ConcurrentLinkedQueue<Integer> mcrawFreeSlots =
            new java.util.concurrent.ConcurrentLinkedQueue<>();
    private String containerMode = PreferenceKeys.CONTAINER_DNG;
    private String mcrawPath;
    private McrawWriter mcrawWriter;                 // owned by the writer thread
    private ExecutorService mcrawEncodeExecutor;
    private ByteBuffer[] mcrawSlots;                 // lazily allocated worst-case buffers
    private int mcrawSlotCapacity;
    private int mcrawMaxInFlight = 1;
    private int mcrawWidth, mcrawHeight, mcrawCropTop, mcrawCropHeight, mcrawStride;
    private boolean mcrawRaw10, mcrawBin;
    private Parameters mcrawParameters;
    // Per-frame capture results keyed by SENSOR_TIMESTAMP, matched at write
    // time to embed exposure/ISO/white balance metadata in each frame.
    private final java.util.TreeMap<Long, CaptureResult> mcrawResults = new java.util.TreeMap<>();
    // Embedded audio: PCM chunks queue here (nanoTime-based timestamps) and
    // are appended to the container on the single writer thread.
    private static final int MCRAW_MAX_AUDIO_CHUNKS = 1024;
    private static final class McrawAudioChunk {
        final long timestampNs;
        final short[] samples;
        McrawAudioChunk(long timestampNs, short[] samples) {
            this.timestampNs = timestampNs;
            this.samples = samples;
        }
    }
    private final java.util.concurrent.ConcurrentLinkedQueue<McrawAudioChunk> mcrawAudioChunks =
            new java.util.concurrent.ConcurrentLinkedQueue<>();
    private final AtomicInteger mcrawAudioBacklog = new AtomicInteger();
    // camera timestamp - System.nanoTime(); converts nanoTime-based audio
    // timestamps into the camera time domain. Long.MIN_VALUE until known.
    private volatile long mcrawCameraTimeOffsetNs = Long.MIN_VALUE;
    // First admitted frame timestamp; audio captured before it has no
    // matching video and is dropped so both timelines start together.
    private volatile long mcrawFirstFrameCameraTs = Long.MIN_VALUE;
    private volatile boolean mcrawAudioActive = false;
    private final AtomicInteger mcrawAudioEnqueued = new AtomicInteger();
    private final AtomicInteger mcrawAudioWritten = new AtomicInteger();
    private final AtomicInteger mcrawAudioDropped = new AtomicInteger();
    private long mcrawTotalEncodedBytes;
    // Written and read only on the single writer thread.
    private long mcrawLastTimestamp = Long.MIN_VALUE;
    private long mcrawEncodedFrames, mcrawEncodeNsTotal, mcrawWriteNsTotal;

    public static class RawVideoStats {
        public final int pendingWrites;
        public final long elapsedMs;
        public final long estimatedBytes;
        public final long availableBytes;

        public RawVideoStats(int pendingWrites, long elapsedMs, long estimatedBytes, long availableBytes) {
            this.pendingWrites = pendingWrites;
            this.elapsedMs = elapsedMs;
            this.estimatedBytes = estimatedBytes;
            this.availableBytes = availableBytes;
        }
    }

    public RawVideoProcessor(ProcessingEventsListener processingEventsListener) {
        super(processingEventsListener);
    }

    @SuppressLint("DefaultLocale")
    public void videoStart(Path outputFolder, ParseExif.ExifData exifData,
                           CameraCharacteristics characteristics,
                           CaptureResult captureResult,
                           CaptureRequest captureRequest,
                           int cameraRotation,
                           ProcessingCallback callback) {
        this.outputFolder = outputFolder;
        this.exifData = exifData;
        this.characteristics = characteristics;
        this.captureResult = captureResult;
        this.cameraRotation = cameraRotation;
        this.captureRequest = captureRequest;
        videoCounter = 0;
        fillParams = false;
        recordingStartMs = System.currentTimeMillis();
        frameWidth = 0;
        frameHeight = 0;
        availableBytesAtStart = 0;
        containerMode = PreferenceKeys.getRawVideoContainer();
        try {
            StatFs statFs = new StatFs(outputFolder.getParent().toString());
            availableBytesAtStart = statFs.getAvailableBlocksLong() * statFs.getBlockSizeLong();
        } catch (Exception ignored) {}
        this.callback = callback;
        if (!isMcrawMode()) {
            // Create output folder if not exists
            try {
                Files.createDirectories(outputFolder);
                if (PreferenceKeys.CONTAINER_DNG.equals(containerMode))
                    Files.createDirectories(outputFolder.resolve(".dng"));
            } catch (IOException e) {
                Log.d(TAG, "Failed to create output directory: " + outputFolder + ", error: " + Log.getStackTraceString(e));
            }
        }
        dngBuffers = new ByteBuffer[writeBufferSize];
        writeBufferCounter = 0;
        if (writeExecutor != null) {
            // Finish any stale tasks from an aborted session before the new
            // one starts, so they cannot touch this session's state.
            writeExecutor.shutdown();
            try {
                writeExecutor.awaitTermination(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        writeExecutor = Executors.newSingleThreadExecutor();
        if (isMcrawMode()) {
            // Single self-contained file: audio and gyro are embedded into
            // the container, so there is no recording folder or FLAC sidecar.
            mcrawAudioChunks.clear();
            mcrawAudioBacklog.set(0);
            mcrawAudioEnqueued.set(0);
            mcrawAudioWritten.set(0);
            mcrawAudioDropped.set(0);
            mcrawCameraTimeOffsetNs = Long.MIN_VALUE;
            mcrawFirstFrameCameraTs = Long.MIN_VALUE;
            mcrawAudioActive = rawAudioRecorder.start(this::enqueueMcrawAudio);
            if (!mcrawAudioActive) {
                Log.w(TAG, "mcraw: microphone unavailable, recording without embedded audio");
            }
            initMcrawState(outputFolder);
        } else {
            Thread th = new Thread(() -> {
                // Always record the rear (away-from-user) microphone
                rawAudioRecorder.start(
                        outputFolder.resolve("RAW_MIC.flac").toString());
            });
            th.start();
        }
        parameters = new Parameters();
        PhotonCamera.getGyro().startVideoRecording(outputFolder, resolveFrameRate());
    }

    private void initMcrawState(Path outputFolder) {
        // The requested folder name becomes the single output file
        // (.../VID_xxx -> .../VID_xxx.mcraw); a suffix disambiguates rapid
        // restarts within the same second, because SAF recreates existing
        // files, which would discard a previous recording.
        Path destination = outputFolder;
        try {
            for (int suffix = 1; Files.exists(destination); suffix++) {
                String name = outputFolder.getFileName().toString();
                destination = outputFolder.resolveSibling(name + "_" + suffix);
            }
        } catch (Exception ignored) {}
        mcrawPath = destination.toString() + ".mcraw";
        mcrawWriter = null;
        mcrawState.set(MCRAW_INITIALIZING);
        mcrawTotalEncodedBytes = 0;
        mcrawLastTimestamp = Long.MIN_VALUE;
        mcrawEncodedFrames = 0;
        mcrawEncodeNsTotal = 0;
        mcrawWriteNsTotal = 0;
        synchronized (mcrawResults) {
            mcrawResults.clear();
        }
        mcrawSlots = new ByteBuffer[MCRAW_SLOT_COUNT];
        mcrawFreeSlots.clear();
        for (int i = 0; i < MCRAW_SLOT_COUNT; i++) {
            mcrawFreeSlots.add(i);
        }
        if (mcrawEncodeExecutor != null) {
            mcrawEncodeExecutor.shutdown();
        }
        mcrawEncodeExecutor = Executors.newFixedThreadPool(MCRAW_ENCODER_THREADS);
        // Hold at most capacity - 2 camera Images open: each in-flight frame
        // pins one ImageReader buffer from when it is admitted until its
        // encode finishes, and the pool must stay circular.
        int capacity = 3;
        try {
            capacity = PhotonCamera.getCaptureController().getRawImageReaderMaxImages();
        } catch (Exception ignored) {}
        mcrawMaxInFlight = Math.max(1, capacity - 2);
        // Create the container on the writer thread. videoStart returns at
        // once, so frames arriving during the slow SAF open are closed on the
        // camera handler thread and the ImageReader keeps circulating.
        writeExecutor.execute(() -> {
            try {
                // Everything the container metadata needs is available here;
                // the pixel array size stands in for rawSize until the first
                // frame resolves the exact capture geometry.
                android.util.Size pixelArray =
                        characteristics.get(CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE);
                android.graphics.Rect activeArray =
                        characteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE);
                Point size = pixelArray != null ? new Point(pixelArray.getWidth(), pixelArray.getHeight())
                        : activeArray != null ? new Point(activeArray.width(), activeArray.height())
                        : new Point(4096, 3072);
                mcrawParameters = new Parameters();
                mcrawParameters.rawSize = size;
                mcrawParameters.FillConstParameters(characteristics, size);
                mcrawParameters.FillDynamicParameters(captureResult, captureRequest, 100);
                mcrawParameters.cameraRotation = cameraRotation;
                long openStart = System.nanoTime();
                mcrawWriter = McrawWriter.open(mcrawPath, buildMcrawContainerMetadata());
                Log.d(TAG, "mcraw container opened in " + (System.nanoTime() - openStart) / 1_000_000.0 + "ms");
                mcrawState.compareAndSet(MCRAW_INITIALIZING, MCRAW_OPENED);
            } catch (Throwable t) {
                Log.e(TAG, "mcraw open failed: " + Log.getStackTraceString(t));
                closeMcrawWriter();
                mcrawState.compareAndSet(MCRAW_INITIALIZING, MCRAW_FAILED);
            }
        });
    }

    private boolean isMcrawMode() {
        return PreferenceKeys.CONTAINER_MCRAW.equals(containerMode);
    }

    int shift = 0;
    @SuppressLint("DefaultLocale")
    public void videoCycle(Image image) {
        if (isMcrawMode()) {
            videoCycleMcraw(image);
        } else {
            videoCycleDng(image);
        }
        videoCounter++;
        processingEventsListener.onProcessingChanged(buildStats());
    }

    @SuppressLint("DefaultLocale")
    private void videoCycleDng(Image image) {
        int format = image.getFormat();
        int startCounter = videoCounter;

        if(!fillParams){
            // Sync gyroflow timestamps to this first camera frame.
            PhotonCamera.getGyro().syncFirstFrame(image.getTimestamp());
            Log.d(TAG, "videoCycle: " + this + " " + image + " " + startCounter);
            int width = image.getWidth();
            int height = image.getHeight();
            if(format == ImageFormat.RAW_SENSOR){
                width = image.getPlanes()[0].getRowStride() / image.getPlanes()[0].getPixelStride();
                // Crop to 16:9
                if(PreferenceKeys.isRawVideoCrop169()) {
                    height = width * 9 / 16;
                    if (ImageSaver.SETTINGS.cropType) {
                        shift = 0;
                    } else {
                        shift = (image.getHeight() - height) / 2;
                        shift -= shift % 2;
                    }
                    shift *= image.getPlanes()[0].getRowStride();
                } else {
                    height = image.getHeight();
                }
            }
            if(format == ImageFormat.RAW10){
                width = image.getPlanes()[0].getRowStride() * 8 / 10; // Include padding pixels into output DNG
                height = image.getHeight();
            }

            frameWidth = width;
            frameHeight = height;

            parameters.rawSize = new Point(width, height);
            parameters.FillConstParameters(characteristics, parameters.rawSize);
            parameters.FillDynamicParameters(captureResult, captureRequest, 100);
            parameters.cameraRotation = this.cameraRotation;
            ParseExif.syncWithParameters(exifData, parameters);
            fillParams = true;
            dngCreator = new DngCreator();
            dngCreator.setParameters(parameters);
            dngCreator.setBinning(PreferenceKeys.isRawVideoDownscale4x());
            dngCreator.setFrameRate(resolveFrameRate());
            dngCreator.setCompression(false);
            if(PreferenceKeys.isRawVideoWriteZip()) {
                String archivePath = outputFolder.resolve("dng.zip").toString();
                int archiveFd = SimpleStorageHelper.openFdForWrite(archivePath);
                if (archiveFd >= 0) {
                    dngCreator.openArchiveByFd(archiveFd);
                } else {
                    dngCreator.openArchive(archivePath);
                }
            }
            /*if(format == ImageFormat.RAW_SENSOR) {
                dngCreator.setBitsPerSample(16);
            }
            if (format == ImageFormat.RAW10) {
                dngCreator.setBitsPerSample(10);
            }*/
            dngCreator.setBitsPerSample(10);
            dngBuffers[0] = dngCreator.dngBuffer(image.getPlanes()[0].getBuffer(), parameters.rawSize.x, parameters.rawSize.y);
            for (int i = 1; i < writeBufferSize; i++) {
                dngBuffers[i] = Allocator.allocateAndCopy(dngBuffers[0].capacity(), dngBuffers[0], 0);
                dngBuffers[i].put(dngBuffers[0]);
                dngBuffers[i].position(0);
            }
            ByteBuffer firstPlane = image.getPlanes()[0].getBuffer();
            int rawBufferCapacity = firstPlane.remaining();
            rawBuffers = new ByteBuffer[writeBufferSize];
            for (int i = 0; i < writeBufferSize; i++) {
                rawBuffers[i] = ByteBuffer.allocateDirect(rawBufferCapacity);
            }
            Log.d(TAG, "DNG buffer allocated, size: " + dngBuffers[0].capacity());
            image.close();
            isRecording = true;
        } else {
            if (!isRecording || writeExecutor == null || dngBuffers[0] == null || rawBuffers == null) {
                image.close();
                return;
            }
            if (pendingWrites.get() >= writeBufferSize) {
                image.close();
                Log.d(TAG, "Dropped frame");
                writeBufferCounter++;
                return;
            }
            final int slot = writeBufferCounter % writeBufferSize;
            @SuppressLint("DefaultLocale")
            String path = outputFolder.resolve(String.format(".dng/RAW_%05d.dng", startCounter)).toString();
            if(PreferenceKeys.isRawVideoWriteZip()) {
                path = String.format("RAW_%05d.dng", startCounter);
            }
            ByteBuffer rawSlot = rawBuffers[slot];
            ByteBuffer planeBuffer = image.getPlanes()[0].getBuffer();
            planeBuffer.rewind();
            rawSlot.clear();
            rawSlot.put(planeBuffer);
            rawSlot.flip();
            image.close();
            pendingWrites.incrementAndGet();
            final String selectedPath = path;
            writeExecutor.execute(() -> {
                try {
                    if (fillParams && dngCreator != null) {
                        dngCreator.writeFile(dngBuffers[slot], rawBuffers[slot], selectedPath, shift);
                    }
                } finally {
                    pendingWrites.decrementAndGet();
                }
            });
            writeBufferCounter++;
        }
    }

    /**
     * MediaCinemaRAW path. Data flow: the camera Image plane is encoded in
     * place (zero copy) by one of the parallel encoders, and the Image is
     * closed the moment encoding finishes - before any disk IO - so the
     * ImageReader pool keeps circulating and frame admission is never gated
     * on storage speed. Encoded frames land in pooled scratch slots which a
     * single ordered writer appends to the container.
     *
     * The container is opened exactly once per recording: the IDLE ->
     * INITIALIZING transition is a compare-and-set, so concurrent or repeated
     * calls can never open a second writer (SAF would recreate the file and
     * discard everything already written).
     */
    private void videoCycleMcraw(Image image) {
        if (writeExecutor == null || mcrawEncodeExecutor == null) {
            image.close();
            return;
        }
        int state = mcrawState.get();
        if (state == MCRAW_INITIALIZING) {
            // SAF file creation is running on the writer thread - close this
            // frame so the ImageReader keeps circulating and the camera
            // handler thread never stalls behind storage IO.
            image.close();
            return;
        }
        if (state == MCRAW_OPENED && mcrawState.compareAndSet(MCRAW_OPENED, MCRAW_READY)) {
            // First frame after the container opened: resolve the capture
            // geometry from it, anchor the gyro timeline, and admit it. The
            // timestamp offset converts nanoTime-based audio timestamps into
            // the camera time domain.
            try {
                computeMcrawGeometry(image);
                PhotonCamera.getGyro().syncFirstFrame(image.getTimestamp());
                mcrawCameraTimeOffsetNs = image.getTimestamp() - System.nanoTime();
                mcrawFirstFrameCameraTs = image.getTimestamp();
            } catch (Throwable t) {
                Log.e(TAG, "mcraw geometry failed: " + Log.getStackTraceString(t));
                mcrawState.set(MCRAW_FAILED);
                image.close();
                return;
            }
            state = MCRAW_READY;
        }
        if (state != MCRAW_READY) {
            // FAILED/ENDED: late callbacks after stop; never re-open the file.
            image.close();
            return;
        }
        // Steady-state admission: bounded by held Images (reader capacity - 2)
        // and by free encoded slots (write backlog).
        if (mcrawHeldImages.get() >= mcrawMaxInFlight || mcrawFreeSlots.isEmpty()) {
            image.close();
            Log.d(TAG, "mcraw dropped frame");
            return;
        }
        final Integer slot = mcrawFreeSlots.poll();
        if (slot == null) {
            image.close();
            return;
        }
        if (mcrawHeldImages.incrementAndGet() > mcrawMaxInFlight) {
            mcrawHeldImages.decrementAndGet();
            mcrawFreeSlots.add(slot);
            image.close();
            return;
        }
        submitMcrawFrame(image, slot);
    }

    private void submitMcrawFrame(Image image, int slot) {
        pendingWrites.incrementAndGet();
        final long frameTs = image.getTimestamp();
        final long receivedMs = System.currentTimeMillis();
        // Result: {encoded length, encode duration ns}.
        final java.util.concurrent.Future<long[]> encoded = mcrawEncodeExecutor.submit(() -> {
            long encodeStart = System.nanoTime();
            try {
                ByteBuffer buffer = mcrawSlots[slot];
                if (buffer == null) {
                    buffer = ByteBuffer.allocateDirect(mcrawSlotCapacity);
                    mcrawSlots[slot] = buffer;
                }
                ByteBuffer plane = image.getPlanes()[0].getBuffer();
                int length = McrawWriter.encode(plane, mcrawWidth, mcrawHeight, mcrawStride,
                        mcrawRaw10, mcrawCropTop, mcrawCropHeight, mcrawBin, buffer);
                return new long[]{length, System.nanoTime() - encodeStart};
            } finally {
                // Release the camera buffer right after encoding; disk IO
                // proceeds independently on the writer thread.
                image.close();
                mcrawHeldImages.decrementAndGet();
            }
        });
        try {
            writeExecutor.execute(() -> {
                try {
                    final long[] result = encoded.get();
                    final int length = (int) result[0];
                    // All container timestamps are relative to the first
                    // frame: long-uptime devices produce sensor timestamps
                    // whose millisecond value overflows 32-bit math in some
                    // readers, breaking audio sync entirely.
                    long ts = frameTs - mcrawFirstFrameCameraTs;
                    if (ts < 0) ts = 0;
                    if (ts <= mcrawLastTimestamp) {
                        ts = mcrawLastTimestamp + 1; // container requires strictly increasing
                    }
                    mcrawLastTimestamp = ts;
                    String frameMetadata = buildMcrawFrameMetadata(ts, receivedMs);
                    drainMcrawAudio();
                    long writeStart = System.nanoTime();
                    mcrawWriter.writeFrame(mcrawSlots[slot], length, ts, frameMetadata);
                    long writeNs = System.nanoTime() - writeStart;
                    mcrawTotalEncodedBytes += length;
                    mcrawEncodedFrames++;
                    mcrawEncodeNsTotal += result[1];
                    mcrawWriteNsTotal += writeNs;
                    Log.d(TAG, "mcraw frame ts=" + ts + " encoded=" + length
                            + "B encode=" + result[1] / 1_000_000.0 + "ms"
                            + " write=" + writeNs / 1_000_000.0 + "ms"
                            + " avgEncode=" + mcrawEncodeNsTotal / 1_000_000.0 / Math.max(1, mcrawEncodedFrames) + "ms"
                            + " avgWrite=" + mcrawWriteNsTotal / 1_000_000.0 / Math.max(1, mcrawEncodedFrames) + "ms"
                            + " total=" + mcrawTotalEncodedBytes + "B writerFrames=" + mcrawWriter.frameCount());
                } catch (Throwable t) {
                    // A transient frame failure must not silently end the
                    // recording: keep the session alive and try the next
                    // frame. Fatal conditions surface again on the following
                    // frames and in close().
                    Log.e(TAG, "mcraw write failed: " + Log.getStackTraceString(t));
                } finally {
                    mcrawFreeSlots.add(slot);
                    pendingWrites.decrementAndGet();
                }
            });
        } catch (java.util.concurrent.RejectedExecutionException e) {
            pendingWrites.decrementAndGet();
            mcrawFreeSlots.add(slot);
            // The encode task still runs and closes the Image.
        }
    }

    /**
     * Resolves the output geometry for one recording from the first frame.
     * The encoder needs even width / height divisible by 4 (by 8 pre-binning),
     * even crop rows to preserve the Bayer phase, and width % 4 for RAW10.
     */
    private void computeMcrawGeometry(Image image) {
        Image.Plane plane = image.getPlanes()[0];
        mcrawStride = plane.getRowStride();
        mcrawRaw10 = image.getFormat() == ImageFormat.RAW10;
        int imageHeight = image.getHeight();
        int width;
        if (mcrawRaw10) {
            width = mcrawStride * 8 / 10; // include padding pixels, same as the DNG path
        } else {
            width = mcrawStride / plane.getPixelStride();
        }
        // The last row may omit stride padding, so never claim more width than
        // the plane buffer actually holds.
        long lastRowBytes = plane.getBuffer().remaining() - (long) (imageHeight - 1) * mcrawStride;
        if (lastRowBytes > 0) {
            int maxWidth = mcrawRaw10 ? (int) (lastRowBytes * 4 / 5)
                                      : (int) (lastRowBytes / plane.getPixelStride());
            if (width > maxWidth) {
                width = maxWidth;
            }
        }
        if (mcrawRaw10) {
            width -= width % 4;
        } else {
            width -= width % 2; // even width keeps the Bayer column phase
        }
        int cropHeight;
        int cropTop = 0;
        if (mcrawRaw10) {
            cropHeight = imageHeight;
        } else if (PreferenceKeys.isRawVideoCrop169()) {
            cropHeight = width * 9 / 16;
            if (!ImageSaver.SETTINGS.cropType) {
                cropTop = (imageHeight - cropHeight) / 2;
                cropTop -= cropTop % 2; // even rows keep the Bayer phase
            }
        } else {
            cropHeight = imageHeight;
        }
        mcrawBin = PreferenceKeys.isRawVideoDownscale4x();
        if (mcrawBin && width % 4 != 0) {
            width -= width % 4;
        }
        // Binned height is cropHeight / 2, so it needs cropHeight % 8 == 0.
        cropHeight -= mcrawBin ? cropHeight % 8 : cropHeight % 4;
        if (cropHeight <= 0 || cropTop > imageHeight - cropHeight) {
            // Degenerate geometry - fall back to a full, un-binned frame.
            cropTop = 0;
            cropHeight = imageHeight - imageHeight % 4;
            mcrawBin = false;
        }
        mcrawWidth = width;
        mcrawHeight = imageHeight;
        mcrawCropTop = cropTop;
        mcrawCropHeight = cropHeight;
        frameWidth = mcrawBin ? width / 2 : width;
        frameHeight = mcrawBin ? cropHeight / 2 : cropHeight;
        // Worst-case encoded size: 16-bit literal payload (2 bytes/sample over
        // the 64-column-padded width) plus both metadata streams and headers.
        long pixels = ((frameWidth + 63) / 64 * 64) * (long) frameHeight;
        mcrawSlotCapacity = (int) Math.min(Integer.MAX_VALUE - 4096, pixels * 2 + pixels / 8 + 4096);
    }

    /** Container metadata in the MediaCinemaRAW reader schema. */
    private String buildMcrawContainerMetadata() {
        JSONObject meta = new JSONObject();
        try {
            String manufacturer = android.os.Build.MANUFACTURER == null ? "" : android.os.Build.MANUFACTURER;
            String model = android.os.Build.MODEL == null ? "" : android.os.Build.MODEL;
            String uniqueCameraModel = (manufacturer + " " + model).trim();
            if (uniqueCameraModel.isEmpty()) uniqueCameraModel = "PhotonCamera";
            meta.put("manufacturer", manufacturer);
            meta.put("model", model);
            // DNG tag 50708 name: readers use it instead of hardcoding a
            // "MotionCam" default when exporting frames.
            meta.put("UniqueCameraModel", uniqueCameraModel);
            meta.put("uniqueCameraModel", uniqueCameraModel);
            int cfa = mcrawParameters != null ? mcrawParameters.cfaPattern : 0;
            meta.put("sensorArrangment", MCRAW_CFA_NAMES[Math.max(0, Math.min(3, cfa))]);
            if (mcrawParameters != null) {
                meta.put("blackLevel", new JSONArray(mcrawParameters.blackLevel));
                meta.put("whiteLevel", mcrawParameters.whiteLevel);
                meta.put("colorMatrix1", new JSONArray(mcrawParameters.ColorMatrix1));
                meta.put("colorMatrix2", new JSONArray(mcrawParameters.ColorMatrix2));
                meta.put("forwardMatrix1", new JSONArray(mcrawParameters.ForwardTransform1));
                meta.put("forwardMatrix2", new JSONArray(mcrawParameters.ForwardTransform2));
                meta.put("calibrationMatrix1", new JSONArray(mcrawParameters.calibrationTransform1));
                meta.put("calibrationMatrix2", new JSONArray(mcrawParameters.calibrationTransform2));
                meta.put("referenceIlluminant1", mcrawParameters.calibrationIlluminant1);
                meta.put("referenceIlluminant2", mcrawParameters.calibrationIlluminant2);
            }
            JSONObject extra = new JSONObject();
            extra.put("formatName", "MediaCinemaRAW");
            extra.put("encoder", "PhotonCamera clean-room implementation");
            extra.put("frameRate", resolveFrameRate());
            extra.put("recordingType", "VIDEO");
            extra.put("useAccurateTimestamp", true);
            // Audio is embedded as PCM16 into the container.
            extra.put("audioSampleRate", rawAudioRecorder.getSampleRate());
            extra.put("audioChannels", mcrawAudioActive ? rawAudioRecorder.getChannels() : 0);
            extra.put("mediaLayout", "embedded");
            meta.put("extraData", extra);
            return meta.toString();
        } catch (JSONException e) {
            Log.e(TAG, "mcraw container metadata failed: " + Log.getStackTraceString(e));
            return "{}";
        }
    }

    /** Per-frame metadata in the MediaCinemaRAW reader schema. */
    private String buildMcrawFrameMetadata(long timestampNs, long receivedTimestampMs) {
        CaptureResult result = takeMcrawResult(timestampNs);
        JSONObject meta = new JSONObject();
        try {
            meta.put("width", frameWidth);
            meta.put("height", frameHeight);
            meta.put("originalWidth", frameWidth);
            meta.put("originalHeight", frameHeight);
            meta.put("rowStride", frameWidth * 2);
            meta.put("compressionType", 7);
            meta.put("isCompressed", true);
            meta.put("pixelFormat", "raw16");
            meta.put("timestamp", Long.toString(timestampNs));
            meta.put("filename", Long.toString(timestampNs));
            meta.put("recvdTimestampMs", Long.toString(receivedTimestampMs));
            meta.put("type", "ZSL");
            meta.put("screenOrientation", cameraRotation);
            Long metadataTimestamp = result == null ? null : result.get(CaptureResult.SENSOR_TIMESTAMP);
            long metadataTsRebased = metadataTimestamp == null ? Long.MIN_VALUE
                    : metadataTimestamp - mcrawFirstFrameCameraTs;
            meta.put("metadataTimestamp", metadataTimestamp == null ? ""
                    : Long.toString(metadataTsRebased));
            meta.put("metadataMatched", metadataTimestamp != null && metadataTsRebased == timestampNs);
            Long exposure = result == null ? null : result.get(CaptureResult.SENSOR_EXPOSURE_TIME);
            Integer iso = result == null ? null : result.get(CaptureResult.SENSOR_SENSITIVITY);
            android.util.Rational[] neutral = result == null ? null : result.get(CaptureResult.SENSOR_NEUTRAL_COLOR_POINT);
            meta.put("exposureTime", exposure == null && mcrawParameters != null
                    ? (long) (mcrawParameters.exposureTime * 1e9) : exposure);
            meta.put("iso", iso == null && mcrawParameters != null ? mcrawParameters.iso : iso);
            float[] wb = mcrawParameters != null ? mcrawParameters.whitePoint : new float[]{1f, 1f, 1f};
            if (neutral != null && neutral.length == 3) {
                wb = new float[]{neutral[0].floatValue(), neutral[1].floatValue(), neutral[2].floatValue()};
            }
            meta.put("asShotNeutral", new JSONArray(wb));
            return meta.toString();
        } catch (JSONException e) {
            return "{\"width\":" + frameWidth + ",\"height\":" + frameHeight + ",\"compressionType\":7}";
        }
    }

    /** Buffers a capture result for the per-frame metadata of a future frame. */
    public void videoCaptureResult(CaptureResult result) {
        Long timestamp = result.get(CaptureResult.SENSOR_TIMESTAMP);
        if (timestamp == null) return;
        synchronized (mcrawResults) {
            mcrawResults.put(timestamp, result);
            while (mcrawResults.size() > 128) {
                mcrawResults.pollFirstEntry();
            }
        }
    }

    private CaptureResult takeMcrawResult(long frameTimestampNs) {
        synchronized (mcrawResults) {
            CaptureResult result = mcrawResults.remove(frameTimestampNs);
            return result != null ? result : captureResult;
        }
    }

    /** PcmSink target; called on the audio thread. Bounds the backlog. */
    private void enqueueMcrawAudio(long timestampNs, short[] samples, int channels) {
        if (mcrawState.get() == MCRAW_ENDED) return;
        if (mcrawAudioBacklog.incrementAndGet() > MCRAW_MAX_AUDIO_CHUNKS) {
            mcrawAudioBacklog.decrementAndGet();
            mcrawAudioDropped.incrementAndGet();
            return;
        }
        mcrawAudioEnqueued.incrementAndGet();
        mcrawAudioChunks.add(new McrawAudioChunk(timestampNs, samples));
    }

    /** Writer thread only. Appends queued audio chunks to the container. */
    private void drainMcrawAudio() {
        McrawAudioChunk chunk;
        while (mcrawWriter != null && (chunk = mcrawAudioChunks.poll()) != null) {
            mcrawAudioBacklog.decrementAndGet();
            long cameraTs = toMcrawCameraTime(chunk.timestampNs) - mcrawFirstFrameCameraTs;
            if (cameraTs < 0) {
                mcrawAudioDropped.incrementAndGet(); // pre-roll without video
                continue;
            }
            try {
                mcrawWriter.writeAudio(chunk.samples, cameraTs);
                mcrawAudioWritten.incrementAndGet();
            } catch (Throwable t) {
                Log.e(TAG, "mcraw audio write failed: " + Log.getStackTraceString(t));
            }
        }
    }

    private long toMcrawCameraTime(long monotonicTimestampNs) {
        long offset = mcrawCameraTimeOffsetNs;
        return offset == Long.MIN_VALUE ? monotonicTimestampNs : monotonicTimestampNs + offset;
    }

    private void closeMcrawWriter() {
        if (mcrawWriter != null) {
            try {
                mcrawWriter.close();
            } catch (IOException e) {
                Log.e(TAG, "mcraw finalize failed: " + Log.getStackTraceString(e));
            }
            mcrawWriter = null;
        }
    }

    private RawVideoStats buildStats() {
        long elapsedMs = System.currentTimeMillis() - recordingStartMs;
        long bytesPerFrame = (long) frameWidth * frameHeight * BITS_PER_SAMPLE / 8;
        long estimatedBytes = bytesPerFrame * videoCounter;
        return new RawVideoStats(pendingWrites.get(), elapsedMs, estimatedBytes, availableBytesAtStart);
    }

    private double resolveFrameRate() {
        switch (PreferenceKeys.getCurrentLensVideoFpsMode()) {
            case 1: return 24.0;
            case 2: return 30.0;
            case 3: return 60.0;
            default: return 30.0; // auto
        }
    }

    private void processVideo() {

    }

    public void videoEnd() {
        isRecording = false;

        // Stop the sensors and microphone first so no samples arrive later.
        if (isMcrawMode()) {
            try {
                // Gyro embedding is disabled: common readers (e.g. Unmcrawesome)
                // abort the whole demux on gyro items, breaking audio playback
                // even when the gyro block is valid per the official decoder.
                PhotonCamera.getGyro().stopVideoRecordingForContainer();
            } catch (Throwable t) {
                Log.e(TAG, "mcraw gyro stop failed: " + Log.getStackTraceString(t));
            }
        } else {
            PhotonCamera.getGyro().stopVideoRecording();
        }
        rawAudioRecorder.stop();
        // Terminal state FIRST after the streams: late frames arriving after
        // this point are closed instead of re-opening the file.
        mcrawState.set(MCRAW_ENDED);
        ExecutorService encodeExecutor = mcrawEncodeExecutor;
        mcrawEncodeExecutor = null;
        if (encodeExecutor != null) {
            encodeExecutor.shutdown(); // queued encodes keep running; write tasks wait on them
        }
        ExecutorService executor = writeExecutor;
        writeExecutor = null;
        if (executor != null) {
            if (isMcrawMode()) {
                try {
                    // All container writes stay on the writer thread: drain
                    // the remaining audio and finalize.
                    executor.execute(() -> {
                        try {
                            drainMcrawAudio();
                        } catch (Throwable t) {
                            Log.e(TAG, "mcraw final stream write failed: " + Log.getStackTraceString(t));
                        } finally {
                            closeMcrawWriter();
                        }
                    });
                } catch (java.util.concurrent.RejectedExecutionException e) {
                    closeMcrawWriter();
                }
            }
            // Drain pending frames before finalizing the archive/container.
            executor.shutdown();
            try {
                executor.awaitTermination(120, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        if (dngCreator != null) {
            dngCreator.closeArchive();
        }
        if (mcrawEncodedFrames > 0) {
            Log.d(TAG, "mcraw summary: frames=" + mcrawEncodedFrames
                    + " avgEncode=" + mcrawEncodeNsTotal / 1e6 / mcrawEncodedFrames + "ms"
                    + " avgWrite=" + mcrawWriteNsTotal / 1e6 / mcrawEncodedFrames + "ms"
                    + " bytes=" + mcrawTotalEncodedBytes
                    + " audioActive=" + mcrawAudioActive
                    + " audioCh=" + rawAudioRecorder.getChannels()
                    + " audioEnqueued=" + mcrawAudioEnqueued.get()
                    + " audioWritten=" + mcrawAudioWritten.get()
                    + " audioDropped=" + mcrawAudioDropped.get());
        }
        if (mcrawWriter != null) {
            closeMcrawWriter(); // fallback if the writer executor was gone
        }
        mcrawSlots = null;
        mcrawFreeSlots.clear();
    }
}
