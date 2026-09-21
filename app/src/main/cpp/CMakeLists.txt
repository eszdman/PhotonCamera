cmake_minimum_required(VERSION 3.18.1)

project(GcamAiUpscaler)

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
    )
    add_link_options("-Wl,-z,max-page-size=16384" "-Wl,-O2" "-Wl,--as-needed" -fopenmp)
endif()

# Find Vulkan
find_package(Vulkan REQUIRED)

# ncnn path
set(NCNN_DIR "${CMAKE_CURRENT_SOURCE_DIR}/ncnn/${ANDROID_ABI}")
find_library(NCNN_LIB ncnn PATHS "${NCNN_DIR}/lib" NO_CMAKE_FIND_ROOT_PATH)
include_directories("${NCNN_DIR}/include/ncnn")

# libjpeg-turbo
set(JPEG_TURBO_DIR "${CMAKE_CURRENT_SOURCE_DIR}/libjpeg-turbo/${ANDROID_ABI}")
# For the environment, we use a stub. In production, use libturbojpeg.a
include_directories("${JPEG_TURBO_DIR}/include")
add_definitions(-DHAVE_TURBOJPEG)

# Sources
set(SRC_DIR ${CMAKE_CURRENT_SOURCE_DIR})

# Main Upscaler Library
add_library(ai_upscaler SHARED
    ${SRC_DIR}/AiUpscalerPipeline.cpp
    ${SRC_DIR}/NativeBridge.cpp
    ${SRC_DIR}/JpegEncoder.cpp
    ${SRC_DIR}/BayerFusion.cpp
    ${SRC_DIR}/allocator.cpp
    ${SRC_DIR}/libjpeg-turbo-stub.cpp
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
add_library(dngCreator SHARED ${SRC_DIR}/dngCreator.cpp)
target_link_libraries(dngCreator log dl)

add_library(flacRecorder SHARED ${SRC_DIR}/flacRecorder.cpp)
target_link_libraries(flacRecorder log)

add_library(camera2native SHARED ${SRC_DIR}/native-engine.cpp)
target_link_libraries(camera2native log)
