cmake_minimum_required(VERSION 3.6)

project(dngCreator)

set(CMAKE_CXX_STANDARD 14)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)

add_link_options("-Wl,-z,max-page-size=16384")

file(READ ${CMAKE_CURRENT_SOURCE_DIR}/../../../version.properties ver)
string(REGEX MATCH "VERSION_BUILD=([0-9]*)" _ ${ver})
set(VERSION_BUILD ${CMAKE_MATCH_1})
string(REGEX MATCH "VERSION_NAME=([0-9,.]*)" _ ${ver})
set(VERSION_NAME ${CMAKE_MATCH_1})

file(DOWNLOAD
        https://raw.githubusercontent.com/ParticlesDevs/tinydng/refs/heads/master/tiny_dng_writer.h
        ${CMAKE_CURRENT_SOURCE_DIR}/deps/tiny_dng_writer.h)

if(NOT EXISTS ${CMAKE_CURRENT_SOURCE_DIR}/deps/technicallyflac.h)
    file(DOWNLOAD
            https://raw.githubusercontent.com/ParticlesDevs/technicallyflac/main/technicallyflac.h
            ${CMAKE_CURRENT_SOURCE_DIR}/deps/technicallyflac.h)
endif()

if(NOT EXISTS ${CMAKE_CURRENT_SOURCE_DIR}/deps/archive.h)
    file(DOWNLOAD
            https://raw.githubusercontent.com/libarchive/libarchive/master/libarchive/archive.h
            ${CMAKE_CURRENT_SOURCE_DIR}/deps/archive.h)
endif()

if(NOT EXISTS ${CMAKE_CURRENT_SOURCE_DIR}/deps/archive_entry.h)
    file(DOWNLOAD
            https://raw.githubusercontent.com/libarchive/libarchive/master/libarchive/archive_entry.h
            ${CMAKE_CURRENT_SOURCE_DIR}/deps/archive_entry.h)
endif()

include_directories(${CMAKE_CURRENT_SOURCE_DIR}/deps)

set(SRC_DIR ${CMAKE_CURRENT_SOURCE_DIR})

find_program(PYTHON3_EXECUTABLE python3)

# ---------------------------------------------------------------------------
# ncnn (prebuilt static lib under ncnn/<ABI>/)
#
# Both ML networks (FlowNet + KernelNet) are compiled into ONE shared library
# (libncnnMl.so) that
# links ncnn statically. The FlowNet model requires three custom layers
# (FlownetUpsample / FlownetCorrLookup / FlownetCorrFused) compiled from source
# and registered at runtime via Net::register_custom_layer().
#
# arm64-v8a: ncnn built with Vulkan → GPU inference, glslang .a linked.
# armeabi-v7a: ncnn built without Vulkan → CPU-only, no glslang needed.
# ---------------------------------------------------------------------------
set(NCNN_ABI_DIR "${CMAKE_CURRENT_SOURCE_DIR}/ncnn/${ANDROID_ABI}")
set(NCNN_INC_DIR "${NCNN_ABI_DIR}/include/ncnn")
set(NCNN_LIB_DIR "${NCNN_ABI_DIR}/lib")

if(EXISTS "${NCNN_LIB_DIR}/libncnn.a")
    # --- generate shader headers from .comp files (configure-time) ---
    set(SHADER_DIR "${CMAKE_CURRENT_SOURCE_DIR}/flownet/layer/vulkan/shader")
    set(GEN_SCRIPT "${CMAKE_CURRENT_SOURCE_DIR}/flownet/gen_shader_header.py")
    set(GEN_OUT_DIR "${CMAKE_CURRENT_BINARY_DIR}/generated")
    file(MAKE_DIRECTORY "${GEN_OUT_DIR}")

    set(VULKAN_LAYER_SRCS "")
    set(VULKAN_LIBS "")
    set(NCNN_VULKAN OFF)

    if(EXISTS "${NCNN_LIB_DIR}/libSPIRV.a")
        set(NCNN_VULKAN ON)
        # Vulkan layer sources — only compiled when glslang is present
        set(VULKAN_LAYER_SRCS
            "${CMAKE_CURRENT_SOURCE_DIR}/flownet/layer/vulkan/flownetupsample_vulkan.cpp"
            "${CMAKE_CURRENT_SOURCE_DIR}/flownet/layer/vulkan/flownetcorrlookup_vulkan.cpp"
            "${CMAKE_CURRENT_SOURCE_DIR}/flownet/layer/vulkan/flownetcorrfused_vulkan.cpp"
        )
        set(VULKAN_LIBS
            "${NCNN_LIB_DIR}/libglslang.a"
            "${NCNN_LIB_DIR}/libMachineIndependent.a"
            "${NCNN_LIB_DIR}/libGenericCodeGen.a"
            "${NCNN_LIB_DIR}/libglslang-default-resource-limits.a"
            "${NCNN_LIB_DIR}/libOSDependent.a"
            "${NCNN_LIB_DIR}/libSPIRV.a"
        )
        # Generate shader headers (only needed for Vulkan path)
        foreach(shader flownetcorrfused flownetcorrlookup flownetupsample)
            set(out "${GEN_OUT_DIR}/${shader}_comp.h")
            execute_process(
                COMMAND "${PYTHON3_EXECUTABLE}" "${GEN_SCRIPT}"
                        "${SHADER_DIR}/${shader}.comp" "${shader}_comp_data"
                OUTPUT_FILE "${out}"
                RESULT_VARIABLE gen_result
            )
            if(NOT gen_result EQUAL 0)
                message(WARNING "Failed to generate ${shader}_comp.h")
            endif()
        endforeach()
    endif()

    add_library(ncnnMl SHARED
        ${SRC_DIR}/ncnnMl.cpp
        ${CMAKE_CURRENT_SOURCE_DIR}/flownet/layer/flownetupsample.cpp
        ${CMAKE_CURRENT_SOURCE_DIR}/flownet/layer/flownetcorrlookup.cpp
        ${CMAKE_CURRENT_SOURCE_DIR}/flownet/layer/flownetcorrfused.cpp
        ${VULKAN_LAYER_SRCS}
    )

    target_include_directories(ncnnMl PRIVATE
        "${NCNN_INC_DIR}"
        "${GEN_OUT_DIR}"
        "${CMAKE_CURRENT_SOURCE_DIR}/flownet"
        "${CMAKE_CURRENT_SOURCE_DIR}/flownet/layer"
        "${CMAKE_CURRENT_SOURCE_DIR}/flownet/layer/vulkan"
    )

    # OpenMP: compile with -fopenmp so the default CPU impls of the custom
    # FlowNet layers (which use `#pragma omp parallel for num_threads(opt.num_threads)`)
    # actually parallelize. Statically link NDK's libomp.a so there is no
    # runtime libomp.so dependency and no runtime CPU/topology probing picks the
    # thread count — ncnnMl sets opt.num_threads explicitly.
    target_compile_options(ncnnMl PRIVATE
        -fno-rtti -fno-exceptions
        -fopenmp
    )

    execute_process(
        COMMAND "${CMAKE_CXX_COMPILER}" -print-resource-dir
        OUTPUT_VARIABLE NCNN_CLANG_RESOURCE_DIR
        OUTPUT_STRIP_TRAILING_WHITESPACE
    )
    if(ANDROID_ABI STREQUAL "arm64-v8a")
        set(NCNN_OMP_ARCH "aarch64")
    elseif(ANDROID_ABI STREQUAL "armeabi-v7a")
        set(NCNN_OMP_ARCH "arm")
    else()
        set(NCNN_OMP_ARCH "")
    endif()

    if(NCNN_OMP_ARCH AND EXISTS "${NCNN_CLANG_RESOURCE_DIR}/lib/linux/${NCNN_OMP_ARCH}/libomp.a")
        set(NCNN_OMP_LIB "${NCNN_CLANG_RESOURCE_DIR}/lib/linux/${NCNN_OMP_ARCH}/libomp.a")
    else()
        set(NCNN_OMP_LIB "")
        message(WARNING "OpenMP static library (libomp.a) not found for ${ANDROID_ABI}")
    endif()

    target_link_libraries(ncnnMl PRIVATE
        "${NCNN_LIB_DIR}/libncnn.a"
        ${VULKAN_LIBS}
        ${NCNN_OMP_LIB}
        android
        log
    )

    set_target_properties(ncnnMl PROPERTIES
        CXX_STANDARD 17
        CXX_STANDARD_REQUIRED ON
        CXX_EXTENSIONS OFF
    )

    message(STATUS "ncnn enabled for ${ANDROID_ABI} (Vulkan: ${NCNN_VULKAN})")
else()
    message(WARNING "ncnn prebuilt not found for ${ANDROID_ABI}; NcnnML disabled")
    # Generate stub JNI functions so the library loads without ncnn.
    file(WRITE "${CMAKE_CURRENT_BINARY_DIR}/ncnnMl_stub.cpp"
        "#include <jni.h>\n"
        "extern \"C\" {\n"
        "JNIEXPORT jboolean JNICALL\n"
        "Java_com_particlesdevs_photoncamera_processing_ml_NcnnMl_nativeEnsureInit(JNIEnv*, jclass) { return JNI_TRUE; }\n"
        "JNIEXPORT jlong JNICALL\n"
        "Java_com_particlesdevs_photoncamera_processing_ml_FlowNetNcnnProcessor_nativeCreate(JNIEnv*, jclass, jobject, jstring) { return 0; }\n"
        "JNIEXPORT jboolean JNICALL\n"
        "Java_com_particlesdevs_photoncamera_processing_ml_FlowNetNcnnProcessor_nativeRun(JNIEnv*, jclass, jlong, jobject, jobject, jint, jint, jobject) { return JNI_FALSE; }\n"
        "JNIEXPORT void JNICALL\n"
        "Java_com_particlesdevs_photoncamera_processing_ml_FlowNetNcnnProcessor_nativeDestroy(JNIEnv*, jclass, jlong) {}\n"
        "JNIEXPORT jlong JNICALL\n"
        "Java_com_particlesdevs_photoncamera_processing_ml_KernelNetNcnnProcessor_nativeCreate(JNIEnv*, jclass, jobject, jstring) { return 0; }\n"
        "JNIEXPORT jboolean JNICALL\n"
        "Java_com_particlesdevs_photoncamera_processing_ml_KernelNetNcnnProcessor_nativeRun(JNIEnv*, jclass, jlong, jobject, jint, jint, jfloat, jobject) { return JNI_FALSE; }\n"
        "JNIEXPORT void JNICALL\n"
        "Java_com_particlesdevs_photoncamera_processing_ml_KernelNetNcnnProcessor_nativeDestroy(JNIEnv*, jclass, jlong) {}\n"
        "}\n"
    )
    add_library(ncnnMl SHARED
        "${CMAKE_CURRENT_BINARY_DIR}/ncnnMl_stub.cpp"
    )
    target_link_libraries(ncnnMl PRIVATE
        log
    )
endif()

add_library(${CMAKE_PROJECT_NAME} SHARED
    ${SRC_DIR}/dngCreator.cpp
)
add_library(allocator SHARED
    ${SRC_DIR}/allocator.cpp
)
add_library(flacRecorder SHARED
    ${SRC_DIR}/flacRecorder.cpp
)
add_library(
        camera2native SHARED ${SRC_DIR}/native-engine.cpp
)

target_compile_definitions(${CMAKE_PROJECT_NAME} PRIVATE
        VERSION_BUILD="${VERSION_BUILD}"
        VERSION_NAME="${VERSION_NAME}"
)

target_link_libraries(${CMAKE_PROJECT_NAME} PRIVATE
        log
        dl
)

target_link_libraries(allocator PRIVATE
        log
        jnigraphics
)

target_link_libraries(flacRecorder PRIVATE
        log
)

target_link_libraries(
        camera2native
        log
)
