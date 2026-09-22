cmake_minimum_required(VERSION 3.18.1)

project(KoshcamNative)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)

# ---------------------------------------------------------------------------
# OPTIMIZATION FOR DIMENSITY 700 (SAMSUNG A22 5G - Cortex-A76/A55 + Mali-G57)
# ---------------------------------------------------------------------------
if(ANDROID_ABI STREQUAL "arm64-v8a")
    add_compile_options(
        -O3
        -mcpu=cortex-a76
        -ffast-math
        -ftree-vectorize
        -fopenmp
        -fomit-frame-pointer
        -fno-rtti
    )
    add_link_options("-Wl,-z,max-page-size=16384" "-Wl,-O2" "-Wl,--as-needed" -fopenmp -static-openmp)
endif()

# Find Vulkan
find_package(Vulkan REQUIRED)

# ncnn path
set(NCNN_DIR "${CMAKE_CURRENT_SOURCE_DIR}/ncnn/${ANDROID_ABI}")
find_library(NCNN_LIB ncnn PATHS "${NCNN_DIR}/lib" NO_CMAKE_FIND_ROOT_PATH)
include_directories("${NCNN_DIR}/include/ncnn")

# libjpeg-turbo
set(JPEG_TURBO_DIR "${CMAKE_CURRENT_SOURCE_DIR}/libjpeg-turbo/${ANDROID_ABI}")
include_directories("${JPEG_TURBO_DIR}/include")
add_definitions(-DHAVE_TURBOJPEG)

# Sources
set(SRC_DIR ${CMAKE_CURRENT_SOURCE_DIR})
include_directories(${SRC_DIR})

# Main Koshcam Library
add_library(ai_upscaler SHARED
    ${SRC_DIR}/AiUpscalerPipeline.cpp
    ${SRC_DIR}/NativeBridge.cpp
    ${SRC_DIR}/JpegEncoder.cpp
    ${SRC_DIR}/BayerFusion.cpp
    ${SRC_DIR}/allocator.cpp
    ${SRC_DIR}/libjpeg-turbo-stub.cpp
    ${SRC_DIR}/ZslRingBuffer.cpp
    ${SRC_DIR}/LutColorEngine.cpp
    ${SRC_DIR}/ResolutionCalculator.cpp
    ${SRC_DIR}/WatermarkEngine.cpp
)

target_link_libraries(ai_upscaler
    Vulkan::Vulkan
    android
    log
    jnigraphics
    z
    dl
)

if(NCNN_LIB)
    target_link_libraries(ai_upscaler ${NCNN_LIB})
    foreach(DEP glslang SPIRV MachineIndependent OSDependent GenericCodeGen)
        find_library(NCNN_${DEP}_LIB ${DEP} PATHS "${NCNN_DIR}/lib" NO_CMAKE_FIND_ROOT_PATH)
        if(NCNN_${DEP}_LIB)
            target_link_libraries(ai_upscaler ${NCNN_${DEP}_LIB})
        endif()
    endforeach()
endif()

# Existing libraries
add_library(ncnnMl SHARED
    ${SRC_DIR}/ncnnMl.cpp
    ${SRC_DIR}/flownet/layer/flownetcorrfused.cpp
    ${SRC_DIR}/flownet/layer/flownetcorrlookup.cpp
    ${SRC_DIR}/flownet/layer/flownetupsample.cpp
    ${SRC_DIR}/flownet/layer/vulkan/flownetcorrfused_vulkan.cpp
    ${SRC_DIR}/flownet/layer/vulkan/flownetcorrlookup_vulkan.cpp
    ${SRC_DIR}/flownet/layer/vulkan/flownetupsample_vulkan.cpp
)

target_include_directories(ncnnMl PRIVATE
    ${SRC_DIR}/flownet
    ${SRC_DIR}/flownet/layer
    ${SRC_DIR}/flownet/layer/vulkan
)

target_link_libraries(ncnnMl
    Vulkan::Vulkan
    android
    log
    jnigraphics
    z
    dl
)

if(NCNN_LIB)
    target_link_libraries(ncnnMl ${NCNN_LIB})
    foreach(DEP glslang SPIRV MachineIndependent OSDependent GenericCodeGen)
        find_library(NCNN_${DEP}_LIB ${DEP} PATHS "${NCNN_DIR}/lib" NO_CMAKE_FIND_ROOT_PATH)
        if(NCNN_${DEP}_LIB)
            target_link_libraries(ncnnMl ${NCNN_${DEP}_LIB})
        endif()
    endforeach()
endif()

add_library(dngCreator SHARED ${SRC_DIR}/dngCreator.cpp)
target_link_libraries(dngCreator log dl)

add_library(flacRecorder SHARED ${SRC_DIR}/flacRecorder.cpp)
target_link_libraries(flacRecorder log)

add_library(camera2native SHARED ${SRC_DIR}/native-engine.cpp)
target_link_libraries(camera2native log)

add_library(allocator SHARED ${SRC_DIR}/allocator.cpp)
target_link_libraries(allocator android log jnigraphics)

set(HALIDE_DIR "${CMAKE_CURRENT_SOURCE_DIR}/halide/${ANDROID_ABI}")
add_library(halidealign SHARED ${SRC_DIR}/halide/align_jni.cpp)
target_include_directories(halidealign PRIVATE ${SRC_DIR}/halide/include ${HALIDE_DIR})
target_link_libraries(halidealign
    ${HALIDE_DIR}/alignburst_base_f16.a
    ${HALIDE_DIR}/alignburst_f16.a
    ${HALIDE_DIR}/libhalide_runtime_android.a
    android
    log
)

add_library(mcraw SHARED
    ${SRC_DIR}/mediacinemaraw/mcraw_jni.cpp
    ${SRC_DIR}/mediacinemaraw/src/ContainerWriter.cpp
    ${SRC_DIR}/mediacinemaraw/src/Encoder.cpp
)
target_include_directories(mcraw PRIVATE ${SRC_DIR}/mediacinemaraw/include)
target_link_libraries(mcraw android log z)
