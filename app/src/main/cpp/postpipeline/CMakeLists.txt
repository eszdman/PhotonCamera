#
# Created by eszdman on 23.11.2026.
#

find_program(POST_GLSLC glslc REQUIRED NO_CMAKE_FIND_ROOT_PATH)
find_program(POST_SPIRV_VAL spirv-val REQUIRED NO_CMAKE_FIND_ROOT_PATH)
find_program(POST_SPIRV_DIS spirv-dis REQUIRED NO_CMAKE_FIND_ROOT_PATH)
set(reflect "${CMAKE_CURRENT_SOURCE_DIR}/tools/reflect_layout.py")
set(assets "${CMAKE_CURRENT_SOURCE_DIR}/../../assets/shaders")
set(interface "${CMAKE_CURRENT_SOURCE_DIR}/../shaders/post/amaze_interface.glsl")
set(includes "${CMAKE_CURRENT_SOURCE_DIR}/tools/shader_includes.py")
set(generator "${CMAKE_CURRENT_SOURCE_DIR}/tools/compile_amaze.py")
set(out "${CMAKE_CURRENT_BINARY_DIR}/generated")
file(MAKE_DIRECTORY "${out}")
find_program(POST_JAVA java REQUIRED NO_CMAKE_FIND_ROOT_PATH)
set(nodes "${CMAKE_CURRENT_SOURCE_DIR}/../../java/com/particlesdevs/photoncamera/processing/opengl/postpipeline")
file(GLOB settings_sources CONFIGURE_DEPENDS "${nodes}/*.java")
set(settings_parser "${CMAKE_CURRENT_SOURCE_DIR}/tools/GeneratePostSettings.java")
set_property(DIRECTORY APPEND PROPERTY CMAKE_CONFIGURE_DEPENDS "${settings_parser}" ${settings_sources})
execute_process(COMMAND "${POST_JAVA}" "${settings_parser}" "${out}/PostSettings.hpp" ${settings_sources}
    COMMAND_ERROR_IS_FATAL ANY)
file(GLOB native_node_sources CONFIGURE_DEPENDS "${CMAKE_CURRENT_SOURCE_DIR}/nodes/*.cpp")
set(tuning_generator "${tuning_dir}/generate.py")
set_property(DIRECTORY APPEND PROPERTY CMAKE_CONFIGURE_DEPENDS ${native_node_sources}
    "${tuning_generator}" "${tuning_dir}/Annotations.hpp")
set(reader_includes "")
set(node_headers "")
foreach(source IN LISTS native_node_sources)
    get_filename_component(node "${source}" NAME_WE)
    # Export the public class declaration. Only the node TU compiles the GPU methods.
    file(READ "${source}" node_text)
    string(FIND "${node_text}" "\n#ifndef PHOTON_DISCOVER_TUNABLES" implementation)
    if(implementation EQUAL -1)
        if(NOT node_text MATCHES "TUNABLE[(]")
            continue()
        endif()
        message(FATAL_ERROR "${source}: GPU implementation must follow #ifndef PHOTON_DISCOVER_TUNABLES")
    endif()
    string(SUBSTRING "${node_text}" 0 ${implementation} declaration)
    file(GENERATE OUTPUT "${out}/nodes/${node}.hpp" CONTENT "${declaration}")
    list(APPEND node_headers "${out}/nodes/${node}.hpp")
    execute_process(COMMAND "${ESD4D_PYTHON}" "${tuning_generator}" "${source}" "${out}/${node}Reader.inc"
        --post-reader --clang "${ESD4D_CLANG}" COMMAND_ERROR_IS_FATAL ANY)
    string(APPEND reader_includes "#include \"${node}Reader.inc\"\n")
endforeach()
file(GENERATE OUTPUT "${out}/PostNodeReaders.inc" CONTENT "${reader_includes}")
set(settings_header "${CMAKE_CURRENT_SOURCE_DIR}/PostPipeline.hpp")
set(settings_layout "${CMAKE_CURRENT_SOURCE_DIR}/tools/generate_settings_layout.py")
set(clang_target_args)
if(CMAKE_CXX_COMPILER_TARGET)
    list(APPEND clang_target_args "--target=${CMAKE_CXX_COMPILER_TARGET}")
endif()
if(CMAKE_SYSROOT)
    list(APPEND clang_target_args "--sysroot=${CMAKE_SYSROOT}")
endif()
add_custom_command(OUTPUT "${out}/PostSettingsLayout.inc"
    COMMAND "${ESD4D_PYTHON}" -B "${settings_layout}" "${out}/PostSettingsLayout.inc" "${ESD4D_CLANG}"
        ${clang_target_args} "-I${out}" "-I${CMAKE_CURRENT_SOURCE_DIR}/.." "-I${CMAKE_CURRENT_SOURCE_DIR}/../esd4d" "${settings_header}"
    DEPENDS ${node_headers} "${tuning_generator}" "${tuning_dir}/Annotations.hpp" "${settings_layout}" "${settings_header}" "${out}/PostSettings.hpp"
        "${CMAKE_CURRENT_SOURCE_DIR}/../framework/esd4d/ComputeContext.hpp" VERBATIM)
add_custom_target(post_settings_layout DEPENDS "${out}/PostSettingsLayout.inc")
add_dependencies(photon_esd4d post_settings_layout)
foreach(pass pad gradcd bound hvwt nyq2 area green nyqref rbpm pmrbint gcorr chroma final)
    set(source "${assets}/amaze/${pass}.glsl")
    set(comp "${out}/amaze_${pass}.comp")
    set(header "${out}/amaze_${pass}.hpp")
    set(spv "${out}/amaze_${pass}.spv")
    add_custom_command(OUTPUT "${spv}" "${header}" BYPRODUCTS "${comp}"
        COMMAND "${ESD4D_PYTHON}" -B "${generator}" "${source}" "${assets}" "${interface}" "${comp}" "${header}" "${spv}.d" "${spv}"
        COMMAND "${POST_GLSLC}" --target-env=vulkan1.0 "${comp}" -o "${spv}.tmp"
        COMMAND "${POST_SPIRV_VAL}" --target-env vulkan1.0 "${spv}.tmp"
        COMMAND "${ESD4D_PYTHON}" "${reflect}" "${POST_SPIRV_DIS}" "${spv}.tmp" "${header}" "${pass}" Tile
        COMMAND "${CMAKE_COMMAND}" -E rename "${spv}.tmp" "${spv}"
        DEPFILE "${spv}.d" DEPENDS "${includes}" "${reflect}" "${source}" "${interface}" "${CMAKE_CURRENT_SOURCE_DIR}/../shaders/post/half_storage.glsl" "${generator}" VERBATIM)
    list(APPEND post_shader_types "${pass}")
    list(APPEND binaries "${spv}")
    list(APPEND headers "${header}")
endforeach()
function(post_fragment name source)
    set(comp "${out}/${name}.comp")
    set(header "${out}/${name}.hpp")
    set(spv "${out}/${name}.spv")
    set(script "${CMAKE_CURRENT_SOURCE_DIR}/tools/compile_fragment.py")
    add_custom_command(OUTPUT "${spv}" "${header}" BYPRODUCTS "${comp}"
        COMMAND "${ESD4D_PYTHON}" -B "${script}" "${source}" "${assets}" "${comp}" "${header}"
            "${spv}.d" "${spv}" "${name}" "${POST_GLSLC}" ${ARGN}
        COMMAND "${POST_GLSLC}" --target-env=vulkan1.0 "${comp}" -o "${spv}.tmp"
        COMMAND "${POST_SPIRV_VAL}" --target-env vulkan1.0 "${spv}.tmp"
        COMMAND "${ESD4D_PYTHON}" "${reflect}" "${POST_SPIRV_DIS}" "${spv}.tmp" "${header}" "${name}" Parameters
        COMMAND "${CMAKE_COMMAND}" -E rename "${spv}.tmp" "${spv}"
        DEPFILE "${spv}.d" DEPENDS "${includes}" "${reflect}" "${source}" "${script}" VERBATIM)
    set(post_shader_types ${post_shader_types} "${name}" PARENT_SCOPE)
    set(binaries ${binaries} "${spv}" PARENT_SCOPE)
    set(headers ${headers} "${header}" PARENT_SCOPE)
endfunction()
post_fragment(monochrome "${assets}/demosaic/monochrome.glsl" --scalar RawBuffer)
post_fragment(demosaicp1 "${assets}/demosaic/demosaicp1.glsl" --scalar RawBuffer --scalar Output)
post_fragment(demosaicp2 "${assets}/demosaic/demosaicp2.glsl" --scalar RawBuffer --scalar GreenBuffer)
post_fragment(demosaicp0quad "${assets}/demosaic/demosaicp0quad.glsl" --scalar RawBuffer)
post_fragment(demosaicp12quad "${assets}/demosaic/demosaicp12quad.glsl" --scalar RawBuffer --scalar Output
    --parameter float:GRADSIZE --parameter float:FUSEMIN --parameter float:FUSEMAX
    --parameter float:FUSESHIFT --parameter float:FUSEMPY --parameter float:NOISEO --parameter float:NOISES)
post_fragment(demosaicp2quad "${assets}/demosaic/demosaicp2quad.glsl" --scalar RawBuffer --scalar GreenBuffer)
post_fragment(convdiff "${assets}/utils/convdiff.glsl" --rg16f Output --define tvar=vec2 --define TEXSIZE=3
    --define "INSIZE=sizes[0]" --define GRADSHIFT=0.0)
post_fragment(esd_gaussdown "${CMAKE_CURRENT_SOURCE_DIR}/../shaders/post/esd_gaussdown.glsl" --parameter int:SCALE)
foreach(scale RANGE 1 4)
    post_fragment(guided${scale} "${assets}/denoise/guidedupsample.glsl" --define SCALE=${scale})
endforeach()
foreach(filter esd3d2_steered esd3d2_steered_prod)
    post_fragment(${filter} "${assets}/denoise/${filter}.glsl" --rg16f GradBuffer --define TONEAWARE=1 --define ESD_PEEL_AXES=1
        --parameter float:NOISES --parameter float:NOISEO --parameter float:MOIRE
        --parameter float:LUMA --parameter float:TONESTRENGTH --parameter float:KERNELSIZE)
endforeach()
foreach(kind bayer quad)
    if(kind STREQUAL "quad")
        set(quad 1)
    else()
        set(quad 0)
    endif()
    foreach(reconstruct RANGE 0 1)
        post_fragment(tofloat_${kind}${reconstruct} "${assets}/Bayer2Float/tofloat.glsl" --zero-border InputBuffer --scalar InputBuffer
            --scalar Output --define QUAD=${quad} --define HLRECON=${reconstruct} --parameter float:HLCLIP
            --parameter ivec2:OFFSET)
    endforeach()
    post_fragment(mask_${kind} "${assets}/Bayer2Float/opposedmask.glsl" --compute 8 8 --scalar u_raw --define QUAD=${quad})
    post_fragment(accum_${kind} "${assets}/Bayer2Float/opposedaccum.glsl" --compute 64 1 --scalar u_raw --define QUAD=${quad})
endforeach()
foreach(reconstruct RANGE 0 1)
    post_fragment(tofloat_rgb${reconstruct} "${assets}/Bayer2Float/tofloat.glsl" --zero-border InputBuffer --define RGBLAYOUT=1
        --define HLRECON=${reconstruct} --parameter float:HLCLIP --parameter ivec2:OFFSET)
endforeach()
post_fragment(mask_rgb "${assets}/Bayer2Float/opposedmask.glsl" --compute 8 8 --define RGBLAYOUT=1)
post_fragment(accum_rgb "${assets}/Bayer2Float/opposedaccum.glsl" --compute 64 1 --define RGBLAYOUT=1)
post_fragment(dilate "${assets}/Bayer2Float/opposeddilate.glsl" --compute 8 8)
post_fragment(rgb_histogram "${CMAKE_CURRENT_SOURCE_DIR}/../shaders/post/rgb_histogram.glsl" --compute 8 8)
post_fragment(levelcorrection "${assets}/ABLC/levelcorrection.glsl")
set(llf "${assets}/local_laplacian2")
post_fragment(down_rgb "${llf}/downsample.glsl" --define INPUT_RGB=1 --scalar Output)
post_fragment(down_luma "${llf}/downsample.glsl" --scalar InputBuffer --scalar Output)
post_fragment(remap_packed "${llf}/remappacked.glsl" --scalar InputBuffer --scalar RemapLut --scalar Output)
post_fragment(reduce_packed "${llf}/reducepacked.glsl" --scalar InputBuffer --scalar Output)
post_fragment(coarse_recon "${llf}/coarserecon.glsl" --scalar PrevRecon --scalar PackedFine
    --scalar PackedCoarse --scalar FineBuffer --scalar Output)
post_fragment(recon_luma "${llf}/reconstruct.glsl" --scalar FineBuffer --scalar CoarseBuffer
    --scalar ReconstructedBuffer --scalar RemapLut --scalar Output)
post_fragment(recon_rgb "${llf}/reconstruct.glsl" --define FINE_RGB=1 --define FINAL_OUTPUT=1
    --scalar CoarseBuffer --scalar ReconstructedBuffer --scalar RemapLut)
post_fragment(capturesharpening "${assets}/CaptureSharpening/capturesharpening.glsl" --define "INSIZE=sizes[0]"
    --parameter float:SHARPSTR --parameter float:SHARPSIZEKER)
post_fragment(correctingflow "${assets}/CorrectingFlow/correctingflow.glsl" --define "SIZE=sizes[0]"
    --parameter vec2:C --parameter vec2:RC --parameter vec2:GC --parameter vec2:BC)
post_fragment(lsharpening3 "${assets}/sharpening/lsharpening3.glsl" --define "INSIZE=sizes[0]"
    --parameter float:SHARPSIZE --parameter float:SHARPMIN --parameter float:SHARPMAX
    --parameter float:NOISES --parameter float:NOISEO --parameter float:INTENSE)
foreach(watermark RANGE 0 1)
    post_fragment(rotate${watermark} "${assets}/RotateWatermark/addwatermark_rotate.glsl" --zero-border InputBuffer --unorm-output --define WATERMARK=${watermark})
endforeach()
set(initial_args --large --define EXPOCURVE=1
    --scalar ExposureCurve --parameter vec3:NEUTRALPOINT
    --parameter float:TONEMAPX1 --parameter float:TONEMAPX2 --parameter float:TONEMAPX3
    --parameter float:SATURATION --parameter float:SATURATION2 --parameter float:CONTRAST
    --parameter float:SHADOWS --parameter float:SATURATIONGAUSS --parameter float:SATURATIONRED
    --parameter float:NOISES --parameter float:NOISEO --parameter float:VIGNETTE
    --parameter float:LTMMIX --parameter float:EPS)
foreach(cube RANGE 0 1)
    foreach(hsv RANGE 0 1)
        foreach(lut RANGE 0 1)
            post_fragment(initial_${cube}${hsv}${lut} "${assets}/Initial/initial.glsl" ${initial_args}
                --define CCT=${cube} --define USE_HSV=${hsv} --define POSTLUT=${lut}
                --float-image HSVMap --unorm PostLut --parameter float:POSTLUTSIZETILES --parameter float:POSTLUTSIZE)
        endforeach()
    endforeach()
endforeach()
foreach(hsv RANGE 0 1)
    post_fragment(modern_${hsv} "${assets}/Initial/modern.glsl" --large --define EXPOCURVE=1 --scalar ExposureCurve
    --parameter vec3:NEUTRALPOINT --parameter float:SATURATION --parameter float:CONTRAST
    --parameter float:SHADOWS --parameter float:BASECONTRAST --parameter float:HIGHLIGHTRANGE
        --define USE_HSV=${hsv} --float-image HSVMap)
endforeach()
post_fragment(headroom "${assets}/headroom/render.glsl" --large --parameter vec3:NEUTRALPOINT)
post_fragment(anisoupscale "${assets}/upscalecrop/anisoupscale.glsl")
post_fragment(bicubic "${CMAKE_CURRENT_SOURCE_DIR}/../shaders/post/bicubic.glsl")
set(deconv "${assets}/capturedeconv")
foreach(packed RANGE 0 1)
    foreach(pass initialize mask smoothmask finish)
        post_fragment(rl_${pass}${packed} "${deconv}/${pass}.glsl" --compute 8 8 --define PACKED=${packed})
    endforeach()
endforeach()
foreach(pass horizontal divide multiply)
    if(pass STREQUAL "horizontal")
        set(vertical 0)
    else()
        set(vertical 1)
    endif()
    if(pass STREQUAL "multiply")
        set(update 1)
    else()
        set(update 0)
    endif()
    post_fragment(rl_${pass} "${deconv}/packed.glsl" --compute 8 8
        --define VERTICAL=${vertical} --define UPDATE=${update} --parameter float:CENTER_WEIGHT
        --parameter float:PAIR_WEIGHT --parameter float:PAIR_OFFSET --parameter float:CORNER_WEIGHT)
endforeach()
foreach(update RANGE 0 1)
    post_fragment(rl_iterate${update} "${CMAKE_CURRENT_SOURCE_DIR}/../shaders/post/rl_iterate.glsl"
        --compute 8 8 --define UPDATE=${update})
endforeach()
post_fragment(scene_luma "${assets}/ultrahdr/sceneluma.glsl" --zero-border InputBuffer --scalar Output)
post_fragment(hdr_gainmap "${assets}/ultrahdr/gainmap.glsl" --scalar LBuffer --unorm InputBuffer --unorm-output)
post_fragment(gainmap_histogram "${CMAKE_CURRENT_SOURCE_DIR}/../shaders/post/gainmap_histogram.glsl"
    --compute 8 8 --scalar Scene --unorm Base)
# Visit exactly the shader types whose modules are embedded below.
set(post_catalog_includes "")
foreach(header IN LISTS headers)
    get_filename_component(filename "${header}" NAME)
    string(APPEND post_catalog_includes "#include \"${filename}\"\n")
endforeach()
set(post_catalog_entries "")
foreach(type IN LISTS post_shader_types)
    string(APPEND post_catalog_entries "    visit(shaders::${type}{});\n")
endforeach()
configure_file("${CMAKE_CURRENT_SOURCE_DIR}/../framework/post/PostShaderCatalog.hpp.in"
    "${out}/PostShaderCatalog.hpp" @ONLY)
cmrc_add_resource_library(post_shaders NAMESPACE post WHENCE "${out}" ${binaries})
target_sources(photon_esd4d PRIVATE PostPipeline.cpp ${native_node_sources}
    ../framework/post/PostRenderer.cpp ../framework/post/PostSettings.cpp
    ../framework/post/PostPass.cpp ../framework/post/PostState.cpp
    ${headers})
target_include_directories(photon_esd4d PUBLIC "${out}" "${CMAKE_CURRENT_SOURCE_DIR}" "${CMAKE_CURRENT_SOURCE_DIR}/..")
target_link_libraries(photon_esd4d PRIVATE post_shaders)
add_executable(post_replay EXCLUDE_FROM_ALL tools/replay.cpp)
target_link_libraries(post_replay PRIVATE photon_esd4d)
add_executable(post_check EXCLUDE_FROM_ALL tools/check.cpp)
target_link_libraries(post_check PRIVATE photon_esd4d)
