#
# Created by eszdman on 21.11.2026.
#

cmake_minimum_required(VERSION 3.22)
project(photon_esd4d LANGUAGES CXX)
if(POLICY CMP0135)
    cmake_policy(SET CMP0135 NEW)
endif()
include(FetchContent)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)
# Compatibility floor for the pinned dependencies on CMake 4.
set(CMAKE_POLICY_VERSION_MINIMUM 3.5)
set(KOMPUTE_OPT_INSTALL OFF CACHE BOOL "")
set(KOMPUTE_OPT_DISABLE_VULKAN_VERSION_CHECK ON CACHE BOOL "")
# Android API 26+ supplies libvulkan in the NDK. Use the same linked
# compute-only loader path as desktop (no Android surface extensions).
find_package(Git REQUIRED)
FetchContent_Declare(kompute
    PATCH_COMMAND "${CMAKE_COMMAND}" "-DGIT=${GIT_EXECUTABLE}" "-DPATCH_NAMES=uniform-buffer,pipeline-cache"
        -P "${CMAKE_CURRENT_SOURCE_DIR}/cmake/PatchKompute.cmake"
    URL https://codeload.github.com/KomputeProject/kompute/tar.gz/267e019ec419ae57f634b341983b60972924b58f)
FetchContent_Declare(cmrc URL https://codeload.github.com/vector-of-bool/cmrc/tar.gz/9a80e17c75fca503a55a38ea011dcfaf2a830cc9)
FetchContent_MakeAvailable(kompute cmrc)
option(ESD4D_FLOWNET "Enable the ncnn dense motion provider" OFF)
include(cmake/Ncnn.cmake)
add_subdirectory(../shaders "${CMAKE_CURRENT_BINARY_DIR}/shaders")
set(model_dir "${CMAKE_CURRENT_SOURCE_DIR}/../../assets/models")
cmrc_add_resource_library(esd4d_model NAMESPACE esdmodel WHENCE "${model_dir}"
    "${model_dir}/kernelnet_v3_params.ncnn.param"
    "${model_dir}/kernelnet_v3_params.ncnn.bin")
find_program(ESD4D_PYTHON NAMES python3 REQUIRED NO_CMAKE_FIND_ROOT_PATH)
set(tuning_dir "${CMAKE_CURRENT_SOURCE_DIR}/../framework/tuning")
set(tunables_header "${CMAKE_CURRENT_SOURCE_DIR}/tuning/Esd4dTunables.hpp")
if(CMAKE_CXX_COMPILER_ID MATCHES "Clang")
    set(ESD4D_CLANG "${CMAKE_CXX_COMPILER}")
else()
    find_program(ESD4D_CLANG NAMES clang++ REQUIRED NO_CMAKE_FIND_ROOT_PATH)
endif()
file(GLOB node_headers CONFIGURE_DEPENDS "${CMAKE_CURRENT_SOURCE_DIR}/nodes/*.hpp")
set_property(DIRECTORY APPEND PROPERTY CMAKE_CONFIGURE_DEPENDS
    ${node_headers} "${CMAKE_CURRENT_SOURCE_DIR}/../framework/kompute/KomputeNode.hpp"
    "${tunables_header}" "${tuning_dir}/Annotations.hpp" "${tuning_dir}/generate.py")
execute_process(COMMAND "${ESD4D_PYTHON}" "${tuning_dir}/generate.py" "${tunables_header}"
    "${CMAKE_CURRENT_BINARY_DIR}/generated/Esd4dBindings.inc" --clang "${ESD4D_CLANG}"
    COMMAND_ERROR_IS_FATAL ANY)
add_library(photon_esd4d STATIC
    ../framework/esd4d/ComputeContext.cpp
    ../framework/esd4d/PipelineCache.cpp
    ../framework/esd4d/Shader.cpp
    ../framework/esd4d/MergeQueue.cpp
    Burst.cpp
    nodes/BurstAlignment.cpp
    nodes/Pyramid.cpp
    nodes/Merge.cpp
    nodes/Bayer.cpp
    nodes/KernelMapNcnn.cpp
    nodes/NormalizeFrames.cpp
    nodes/EstimateNoise.cpp
    nodes/RepairHotPixels.cpp
    nodes/PackReference.cpp
    nodes/StartAlignment.cpp
    nodes/GenerateKernelMap.cpp
    nodes/MergeFrames.cpp)
if(ANDROID)
    # Full-resolution RAW unpacking, normalization and packing run on the CPU during capture.
    target_compile_options(photon_esd4d PRIVATE "$<$<CONFIG:Debug>:-O2>")
    set_property(SOURCE nodes/KernelMapNcnn.cpp APPEND PROPERTY COMPILE_OPTIONS -fno-rtti)
endif()
add_dependencies(photon_esd4d esd_shader_interface)
target_compile_features(photon_esd4d PUBLIC cxx_std_17)
target_link_libraries(photon_esd4d PRIVATE kompute::kompute esd4d_shaders esd4d_model esd4d_ncnn)
target_include_directories(photon_esd4d PUBLIC "${CMAKE_CURRENT_SOURCE_DIR}/.." "${CMAKE_CURRENT_SOURCE_DIR}" "${CMAKE_CURRENT_BINARY_DIR}/generated")
include(cmake/HalideAlignment.cmake)
include(cmake/FlowNet.cmake)
add_subdirectory(../postpipeline "${CMAKE_CURRENT_BINARY_DIR}/postpipeline")
if(CMAKE_CURRENT_SOURCE_DIR STREQUAL CMAKE_SOURCE_DIR)
    enable_testing()
    set_property(TARGET post_check PROPERTY EXCLUDE_FROM_ALL FALSE)
    add_test(NAME post_pipeline_graph COMMAND post_check)
    set(post_includes "${CMAKE_CURRENT_BINARY_DIR}/postpipeline/compiler-includes.txt")
    file(GENERATE OUTPUT "${post_includes}" CONTENT "$<JOIN:$<TARGET_PROPERTY:photon_esd4d,INCLUDE_DIRECTORIES>,
>")
    add_test(NAME post_shader_contracts COMMAND "${ESD4D_PYTHON}"
        "${CMAKE_CURRENT_SOURCE_DIR}/../postpipeline/tools/check_bindings.py" "${CMAKE_CXX_COMPILER}" "${post_includes}")
    find_package(Java QUIET COMPONENTS Development)
    set(tuning_test "${ESD4D_PYTHON}" "${tuning_dir}/test_bindings.py" "${ESD4D_CLANG}")
    if(Java_Development_FOUND AND NOT CMAKE_CROSSCOMPILING)
        list(APPEND tuning_test "${Java_JAVAC_EXECUTABLE}" "${Java_JAVA_EXECUTABLE}")
    endif()
    add_test(NAME tunable_field_discovery COMMAND ${tuning_test})
    add_executable(esd4d_burst tools/burst_replay.cpp)
    target_link_libraries(esd4d_burst PRIVATE photon_esd4d)
    add_executable(esd4d_replay tools/replay.cpp)
    target_link_libraries(esd4d_replay PRIVATE photon_esd4d)
    add_executable(esd4d_check tools/check.cpp)
    target_link_libraries(esd4d_check PRIVATE photon_esd4d)
    add_executable(esd4d_burst_check tools/burst_check.cpp)
    target_link_libraries(esd4d_burst_check PRIVATE photon_esd4d)
    add_test(NAME esd4d_burst_pipeline COMMAND esd4d_burst_check)
    add_test(NAME esd4d_combine COMMAND esd4d_check)
    add_test(NAME esd4d_pipeline_cache COMMAND "${CMAKE_COMMAND}" "-DRUNNER=$<TARGET_FILE:esd4d_burst>"
        -P "${CMAKE_CURRENT_SOURCE_DIR}/cmake/CheckPipelineCache.cmake")
endif()
