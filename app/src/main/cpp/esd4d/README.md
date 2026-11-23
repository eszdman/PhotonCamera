<!--
Created by eszdman on 19.11.2026.
-->

# Native ESD4D

`processBurst` in `Burst.hpp` takes sensor metadata and a sequence of Bayer frames.
It returns the merged, sensor-sized normalized FP16 Bayer plane, the KernelNet
parameter map and its dimensions, the applied and measured noise models, and any
confirmed hot-pixel sites. Pass the frame vector with `std::move` to transfer
ownership and release each alternate frame after upload.

`buildDefaultPipeline` in `Burst.cpp` lists the native nodes in execution order,
like `PostPipeline.java`. A stage is a `BurstNode` subclass in `nodes/` with
a `run(BurstState&)` method. Register it with `pipeline.add<MyNode>()` where
its inputs are ready and add its source to the CMake target. The pipeline owns
the nodes for one burst. Desktop replay and Android capture use the same classes.

Edit the ESD4D warp and combine in `nodes/Merge.cpp`. Both dispatches show the
shader, parameters and buffers there. The two-slot queue, uploads, fences and
readback live in `../framework/esd4d/MergeQueue.cpp` and its header.
Post-processing stages live together in `../postpipeline/nodes/`; their graph is
in `../postpipeline/PostPipeline.cpp`. Shared Kompute wrappers, post pass
recording and tunable discovery live under `../framework/`.

`RepairHotPixels.hpp` and `RepairHotPixels.cpp` give a full shader-backed example.
The node detects candidate Bayer sites on the reference, confirms persistent
defects across sampled frames, then repairs each frame using the confirmed sites.
`detectHotPixelCandidates` owns the shader bindings, dispatch and proposal readback.
Its GPU buffers are local to the pass. Data needed
by later nodes, such as the corrected Bayer frames, lives in `BurstState`.

`StartAlignment` launches the Halide worker before KernelNet runs. During
`MergeFrames`, `awaitNextFrame()` transfers each alternate and its warp in exposure
order once the matcher has finished reading that frame. The merge stages the
pixels in an upload slot before the alternate goes out of scope. A stage that submits GPU work controls
its own synchronization.

Once burst calibration finishes, the merge feeds Vulkan through two reusable
upload slots. The CPU can pack the next Bayer frame while the GPU warps and
combines the previous one. Reusing a slot waits for its fence, and requesting
the final Bayer plane drains the queue. Both slots share the device-side source
and warp output, so the upload buffer count stays fixed as the burst grows.

PostPipeline and ESD4D share the typed shader API. `setVar` supplies a reflected
shader parameter. `setVars(params)` copies the full generated `Shader::Parameters`
block. `bindInputs(...).writeTo(...)` records the operation in
the caller's Kompute sequence. The merge calls `evalAsync()` once per frame,
after recording warp and combine. Each dispatch records its own push constants.
Each slot owns its cached descriptors until its sequence completes.

The pipeline normalizes RAW counts using per-site black levels, estimates noise
from exposure- and ISO-matched frames, optionally verifies temporal hot pixels,
and runs the embedded KernelNet model in overlapping tiles. It matches each
alternate to the reference, warps the CFA samples and accumulates on Vulkan.
The warp, robust/scheduled merge and block-matcher scoring share GLSL with the
GLES implementation. Packed FP16 storage buffers and explicit tap interpolation
avoid format-dependent storage-image and sampler features. Burst accumulation
uses full-frame buffers and requires five storage descriptors and eight bytes
per packed Bayer quad within the device's storage-buffer range.

Workgroup shape depends on the shader and the Vulkan device ID. The profiled
Radeon RX 9070 XT uses 64 × 1 for KernelNet, robust combine, and the Lanczos2
warp with per-tile motion and zero residual fusion. On the profiled Android
Adreno 740, only KernelNet uses 64 × 1. Other passes, unmatched devices and
dispatches exceeding the device's row-count limit use 8 × 8.

## Desktop

Install Python 3, Clang for tunable discovery, CMake, a C++17 compiler, the Vulkan SDK/loader and a compute-capable
driver. Put the host tools `glslc` and `spirv-val` on PATH. Install the Halide
development package for the default precise matcher. With Halide available,
CMake builds the included generator and caches its native kernels. Without
Halide, the Kompute block matcher handles automatic alignment.

From the app directory run

```sh
cmake -S src/main/cpp/esd4d -B build/esd4d-pc -G Ninja -DCMAKE_BUILD_TYPE=Release
cmake --build build/esd4d-pc
ctest --test-dir build/esd4d-pc --output-on-failure
build/esd4d-pc/esd4d_burst burst.txt merged.f16
```

Vulkan entry points and private includes live in `../shaders/esd4d/`. The alignment
and merge shader bodies shared with GLES live in `../shaders/shared/shaders/`.
Gradle packages this shared source at the existing `shaders/` asset paths.
The first configure downloads the pinned Kompute and CMakeRC sources. Each GLSL
entry and its transitive includes have build-time dependencies. CMake compiles
shader variants to Vulkan 1.0 SPIR-V, validates them and embeds them with CMakeRC.
Editing a common include rebuilds its consumers. Shader compilation and model
files for KernelNet are independent of the process working directory.

A replay manifest starts with width, height, CFA (RGGB=0, GRBG=1, GBRG=2,
BGGR=3), white level, four black levels in sensor raster order, shot and read
variance coefficients, and matcher. Each subsequent line specifies a plane
file, sample format, exposure layer multiplier, and ISO. The first frame is the
reference. Frame paths are relative to the manifest and may be quoted.

```text
4096 3072 3 1023 64 64 64 64 0.00001936 0.000000049 halide
reference.u16 u16 1 1118
alternate.u16 u16 1 1118
short.u16 u16 2 1118
```

Plane files contain contiguous little-endian 16-bit samples. Use `u16` for
sensor counts or `f16` for normalized binary16. The C++ helper
`unpackSensorBits` decodes PhotonCamera's contiguous LSB-first packed burst
format before normalization. The replay output is a headerless binary16 plane.
Use `--fixed-noise` to replay the supplied noise coefficients directly and
`--merge-strength stops` to reproduce the camera setting.
Matcher choices are `halide`, `lk` (Halide with Lucas–Kanade refinement),
`pyramid` and `off`. The API also accepts per-frame gyro/OIS homographies in
column-major raw-pixel coordinates and a caller-supplied motion provider.
Camera metadata conversion and persistent noise storage belong to the caller.
Supply a stored calibration and commit callback through `BurstOptions::calibration`.

Enable the optional FlowNet provider with `-DESD4D_FLOWNET=ON`. Desktop builds
use an installed ncnn package (`ncnn_DIR` can point to its CMake config).
Select it in the manifest with `flownet "model.param" "model.bin"` in place of
`halide`, or set `BurstOptions::motion` using `makeFlowNetMotion`. This provider
runs the existing ncnn graph and custom layers on CPU. Kompute performs the
guided flow upsampling, warp and merge.

## ESD4D tunables

Declare annotated fields in the node's nested `Settings` struct. Include that
type as a public base of `Esd4dTunables` in `tuning/Esd4dTunables.hpp`.
Each new field in that struct is automatically included in the preferences
and the packed snapshot. A checkbox takes the field's native type and default.

```cpp
TUNABLE("Merge", "CFA residual robustness")
bool cfaResidualRobustness = true;
```

Read the typed settings in the node, e.g. `state.settings<MergeFrames>().cfaResidualRobustness`.
For a numeric slider append its minimum, maximum and step to `TUNABLE`.
For a menu use an enum class. An enumerator may carry a `TUNABLE_LABEL` for display.
The existing field and enumerator names identify saved preferences.

Clang discovers annotated fields of the `TUNABLE_GROUP` and evaluates their
C++ default initializers. CMake generates the native member visitor. Gradle
uses the NDK's Clang to generate the Java preferences and array writer.
Both builds reject unsupported field types, invalid ranges, and defaults outside
the range. Android transfers one packed array per merge, with a schema identifier
in the first slot.

The JNI entry point refreshes the live object with `photon::tuning::inject(opt.tunables, values)`,
where `values` is the packed Java snapshot. Desktop callers can use
`photon::tuning::inject(opt.tunables, stream)` for named overrides. Only annotated
fields are updated, and an invalid update leaves the object intact. During a
burst, processing classes read `options.tunables` directly.

A settings class may also live entirely in a `.cpp`. Pass that source as the
group input to the bindings generator, and include the generated bindings at
global scope after the class definition. Guard the bindings include and the
code using it with `#ifndef PHOTON_DISCOVER_TUNABLES` so discovery works on a
clean build. Private fields can grant access with `friend struct
photon::tuning::Schema<MyClass>;`. The group must support constant default
construction so Clang can evaluate its initial values. A processor that needs
runtime constructor arguments can hold the settings group as a member.
See `../framework/tuning/tests/Settings.cpp` for a compiled example.

Desktop replay accepts the same field names and string choices, one per line.
For example, a file named `settings.txt` can contain

```properties
cfaResidualRobustness=false
halideMergeSampling=cubic
noiseMpy=1.25
```

Run `esd4d_burst burst.txt merged.f16 --tunables settings.txt`. Omitted fields
keep the manifest settings and schema defaults. `--fixed-noise` takes precedence
over the file. On Android, alignment comparison and GPU sync profiling select GLES.
The legacy fields `noiseOFloorMpy`, `adaptiveFallbackMin` and `adaptiveFallbackMax`
are carried for preference compatibility. The current noise estimators do not use them.

## Device pipeline cache

Both ESD4D merge and native PostPipeline ship their shader variants as validated
SPIR-V. The build resolves GLSL defines. At runtime Vulkan creates
compute pipelines for the local driver. A disk cache lets the driver reuse
previous work across processes. The driver decides how much work a cache hit saves.

On PC, warm up and replay with the same writable cache directory:

```sh
build/esd4d-pc/esd4d_burst --prepare-pipelines .cache/esd4d
build/esd4d-pc/esd4d_burst burst.txt merged.f16 --pipeline-cache .cache/esd4d
build/esd4d-pc/postpipeline/post_replay sdr 4096 3072 1024 merged.f16 out.rgba8 --pipeline-cache .cache/esd4d
```

C++ callers use `preparePipelines(directory)` at startup and pass the same directory
in `BurstOptions::pipelineCacheDirectory` or to the `ComputeContext` used by
`renderPostPipeline`. Warm-up compares each embedded module's
binary and pipeline layout, creates missing or changed pipelines without a dispatch,
and atomically saves the Vulkan cache. An unchanged launch skips pipeline creation
during warm-up. A driver, GPU or ABI change selects a fresh cache. An unreadable or
corrupt snapshot is rebuilt. A burst can also run with an empty cache directory.

Startup prepares every device-supported variant from both shader catalogs. Tunables
such as the ESD3D2 version select an already prepared shader. Numeric settings
such as moire strength update dispatch parameters. Both reuse the startup cache.

## Android

The Android app builds the native backend by default and warms its pipelines on a
background executor. HdrxProcessor transfers the selected burst frames through JNI.
PostPipeline receives the merged normalized FP16 Bayer plane and RGBA16F KernelNet
map. Single-frame captures keep their original path. FlowNet, alignment debug
comparison, unsupported device limits and unsupported input layouts use GLES.
Build with `-PphotonBuildEsd4d=false` to use GLES for every burst.

The static target is `photon_esd4d`. Enable it from the app's native CMake
project with `-DPHOTON_BUILD_ESD4D=ON`, or cross-compile the same standalone
runner and checks with the NDK. For example

```sh
cmake -S src/main/cpp/esd4d -B build/esd4d-arm64 -G Ninja \
  -DCMAKE_TOOLCHAIN_FILE="$ANDROID_NDK_HOME/build/cmake/android.toolchain.cmake" \
  -DANDROID_ABI=arm64-v8a -DANDROID_PLATFORM=android-26 -DCMAKE_BUILD_TYPE=Release
cmake --build build/esd4d-arm64
```

Arm64 uses the app's packaged Halide AOT kernels. Other Android ABIs use the
Kompute block matcher. Both platforms expose the same C++ burst entry point.

## Aligned-frame replay

`RobustMerge::addAligned` and `esd4d_replay` accept already warped frames for
stage-by-stage comparisons. Reference, aligned inputs and output use RGBA
binary16 quads with dimensions `(rawHalfWidth + shiftX) × (rawHalfHeight +
shiftY)`. The KernelNet map stores sigma X, sigma Y, correlation, and one.
Each combine dispatch pushes one `MergeParameters` block containing geometry,
noise, exposure, frame index and scheduled weight.

```sh
build/esd4d-pc/esd4d_replay 2000 1500 0 0 1000 750 reference.f16 kernels.f16 merged.f16 \
  aligned1.f16 0.001 0.00001 1.0 aligned2.f16 0.001 0.00001 0.5
```
