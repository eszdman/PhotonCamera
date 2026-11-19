# PhotonCamera CRAW integration

Vendored from https://github.com/matthew777777/CinematicRAWStudio-Encoder
at commit `9bc1f30f71fe45b19344033ecb58bb3cf5a6a399` (Apache-2.0; LICENSE and NOTICE
are retained in vendor/). The app labels codec 1 Fast and codec 3 Live (the pinned upstream snapshot calls codec 1 Balanced).

Local vendor changes: Writer accepts a POSIX file descriptor (duplicates it for
SAF, without reopening /proc or truncating a second time) and closes the stream
if header/META construction fails. Codec sources are unmodified. Tools are not
vendored or built. C++20 and optimization apply only to these native targets.

RAW10 is unpacked without losing low bits. RAW16 keeps all 16 storage bits,
independent of potentially stale camera white-level calibration. Optional crop
and same-colour binning follow the app's RAW video settings. The container stores
actual output geometry, original sensor calibration, source crop and binning.
Audio is interleaved PCM16; gyro and accelerometer retain separate sample times.
Camera2 shading maps are deduplicated into LSHD chunks referenced by frames.
Device capture testing is needed to establish sustained frame rates.

Validation:

- `cmake -S src/test/cpp/craw -B /tmp/craw-tests -DCMAKE_BUILD_TYPE=Release`
- `cmake --build /tmp/craw-tests -j4`
- `/tmp/craw-tests/craw_integration /tmp/craw-cases`
- `python3 src/test/cpp/craw/verify.py /path/to/CinematicRAWStudio-Spec/reference /tmp/craw-cases`

The reference check requires NumPy. It decodes 32 cases independently and checks
chunk CRCs, shading, interleaved PCM, sensor timestamps, ordering and trailer.
`DeviceSmoke.java` also exercises the production JNI bridge under Android
`app_process`, without opening the camera/microphone or installing the app.
Compile it with CrawWriter, RawContainerWriter, CrawMetadata and a test-only
SimpleStorageHelper stub returning -1; dex it with the SDK, then run with the
built libcraw.so and matching libc++_shared.so on the device library path.

Camera2 values absent on a device are omitted. Gyro and accelerometer are the
IMU types supported by the upstream format. REALTIME cameras share the sensor
clock; UNKNOWN camera clocks use an explicitly marked first-frame estimate.
LSHD frame references point to maps remapped to the stored crop; source_lshd_ref
retains each original active-array map. Calibration and all available Camera2
characteristic/result keys are retained alongside the CRAW schema fields.
