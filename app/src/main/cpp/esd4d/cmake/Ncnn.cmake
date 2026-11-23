#
# Created by eszdman on 21.11.2026.
#

add_library(esd4d_ncnn INTERFACE)
if(ANDROID AND EXISTS "${CMAKE_CURRENT_SOURCE_DIR}/../ncnn/${ANDROID_ABI}/lib/libncnn.a")
    set(root "${CMAKE_CURRENT_SOURCE_DIR}/../ncnn/${ANDROID_ABI}")
    target_include_directories(esd4d_ncnn INTERFACE "${root}/include/ncnn")
    target_link_libraries(esd4d_ncnn INTERFACE "${root}/lib/libncnn.a")
    if(EXISTS "${root}/lib/libSPIRV.a")
        foreach(lib glslang MachineIndependent GenericCodeGen glslang-default-resource-limits OSDependent SPIRV)
            target_link_libraries(esd4d_ncnn INTERFACE "${root}/lib/lib${lib}.a")
        endforeach()
    endif()
    if(ANDROID_ABI STREQUAL "arm64-v8a")
        target_link_libraries(esd4d_ncnn INTERFACE "${root}/lib/libomp.a")
    else()
        target_link_libraries(esd4d_ncnn INTERFACE -fopenmp -static-openmp)
    endif()
    target_link_libraries(esd4d_ncnn INTERFACE android log)
else()
    find_package(ncnn CONFIG REQUIRED)
    target_link_libraries(esd4d_ncnn INTERFACE ncnn)
endif()
