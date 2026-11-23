# Rebuilding libncnn.a

The arm64 library uses ncnn commit `a2d60e2b378001312b173baa61c33e015006776e` with its glslang submodule at `fe88f421038e1bb0a25cd5c1b2dfe505db82d08f`. Both native targets link the arm64 static LLVM OpenMP runtime in this directory. Android schedules its worker threads.

From that ncnn source tree, with `ANDROID_NDK` pointing to NDK 27.2.12479018, run

```sh
cmake -S . -B build-arm64 -G Ninja \
    -DCMAKE_TOOLCHAIN_FILE="$ANDROID_NDK/build/cmake/android.toolchain.cmake" \
    -DANDROID_ABI=arm64-v8a -DANDROID_PLATFORM=android-26 \
    -DCMAKE_BUILD_TYPE=Release -DCMAKE_POLICY_VERSION_MINIMUM=3.5 \
    -DNCNN_VERSION=20260817 -DNCNN_VULKAN=ON -DNCNN_OPENMP=ON \
    -DNCNN_SIMPLEOMP=OFF -DNCNN_BUILD_TESTS=OFF -DNCNN_BUILD_TOOLS=OFF \
    -DNCNN_BUILD_EXAMPLES=OFF -DNCNN_BUILD_BENCHMARK=OFF
cmake --build build-arm64 --target ncnn
"$ANDROID_NDK/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-strip" \
    --strip-debug build-arm64/src/libncnn.a
```

Copy the resulting archive to `lib/libncnn.a` and `build-arm64/src/platform.h` to `include/ncnn/platform.h`.


## Rebuilding libomp.a

Use the `openmp` and `cmake` directories from LLVM tag `llvmorg-18.1.8`, next to each other. The NDK runtime aborted intermittently while enumerating CPU affinity during model initialization on Snapdragon 8 Gen 2. The arm64 build disables that feature at compile time and retains the OpenMP parallel loops.

From the LLVM source root, run

```sh
sed -i 's/^    !KMP_OS_WASI$/    !KMP_OS_WASI \&\& !defined(__ANDROID__)/' openmp/runtime/src/kmp_os.h
cmake -S openmp -B build-omp -G Ninja \
    -DCMAKE_TOOLCHAIN_FILE="$ANDROID_NDK/build/cmake/android.toolchain.cmake" \
    -DANDROID_ABI=arm64-v8a -DANDROID_PLATFORM=android-26 \
    -DCMAKE_BUILD_TYPE=Release -DCMAKE_POLICY_VERSION_MINIMUM=3.5 \
    -DOPENMP_ENABLE_LIBOMPTARGET=OFF -DLIBOMP_ENABLE_SHARED=OFF \
    -DLIBOMP_OMPT_SUPPORT=OFF -DLIBOMP_USE_VERSION_SYMBOLS=OFF
cmake --build build-omp --target omp
"$ANDROID_NDK/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-strip" \
    --strip-debug build-omp/runtime/src/libomp.a
```

Copy `build-omp/runtime/src/libomp.a` to `lib/libomp.a`. Its license is in `LICENSE-libomp.txt`.
