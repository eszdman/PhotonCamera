#
# Created by eszdman on 19.11.2026.
#

find_program(GLSLC glslc REQUIRED NO_CMAKE_FIND_ROOT_PATH)
find_program(SPIRV_VAL spirv-val REQUIRED NO_CMAKE_FIND_ROOT_PATH)
set(shader_includes "${CMAKE_CURRENT_SOURCE_DIR}/esd4d/include")
set(shared_glsl "${CMAKE_CURRENT_SOURCE_DIR}/shared/shaders")
set(esd4d_row_width 64)
# Row layouts measured faster for these shader variants on the profiled GPU.
set(row_shaders "")
if(NOT ANDROID)
    list(APPEND row_shaders combine warp_4_0)
endif()
set(row_catalog "")
foreach(name IN LISTS row_shaders)
    string(APPEND row_catalog "    \"${name}\",\n")
endforeach()
file(CONFIGURE OUTPUT "${CMAKE_CURRENT_BINARY_DIR}/esd4d_row_shaders.inc"
    CONTENT "@row_catalog@" @ONLY)
function(esd_shader_variant name source row)
    cmake_parse_arguments(VARIANT "" "PASS" "" ${ARGN})
    set(shader "${CMAKE_CURRENT_SOURCE_DIR}/esd4d/${source}")
    set(spv "${CMAKE_CURRENT_BINARY_DIR}/${name}.spv")
    add_custom_command(OUTPUT "${spv}"
        COMMAND "${GLSLC}" --target-env=vulkan1.0 "-I${shader_includes}" "-I${shared_glsl}" ${VARIANT_UNPARSED_ARGUMENTS} -MD -MT "${spv}" -MF "${spv}.d" "${shader}" -o "${spv}.tmp"
        COMMAND "${SPIRV_VAL}" --target-env vulkan1.0 "${spv}.tmp"
        COMMAND "${CMAKE_COMMAND}" -E rename "${spv}.tmp" "${spv}"
        DEPENDS "${shader}"
        DEPFILE "${spv}.d" VERBATIM)
    set(layout "${VARIANT_PASS}")
    if(NOT layout)
        get_filename_component(layout "${source}" NAME_WE)
    endif()
    string(APPEND catalog "    ESD_VARIANT(\"${name}\", ${layout}, ${row})\n")
    set(catalog "${catalog}" PARENT_SCOPE)
    set(shaders ${shaders} "${spv}" PARENT_SCOPE)
endfunction()
function(esd_shader name source)
    esd_shader_variant(${name} ${source} false ${ARGN})
    if(name IN_LIST row_shaders)
        esd_shader_variant(${name}_row ${source} true -DESD4D_ROW_WIDTH=${esd4d_row_width} ${ARGN})
    endif()
    set(shaders ${shaders} PARENT_SCOPE)
    set(catalog "${catalog}" PARENT_SCOPE)
endfunction()
esd_shader(hot_pixels hot_pixels.comp)
esd_shader(noise_blend noise.comp -DHISTOGRAM=0)
esd_shader(noise_histogram noise.comp -DHISTOGRAM=1)
esd_shader(pyramid_match pyramid_match.comp)
esd_shader(pyramid_prepare pyramid_prepare.comp)
if(ANDROID)
    esd_shader(combine combine_pooled.comp PASS combine)
else()
    esd_shader(combine combine.comp)
endif()
esd_shader(combine_scheduled combine_scheduled.comp PASS combine)
esd_shader(warp_gyro warp.comp -DGYRO_WARP=1)
esd_shader(warp_flow warp.comp -DFLOW=1)
esd_shader(warp_floor warp.comp)
foreach(mode RANGE 1 4)
    foreach(field RANGE 0 1)
        esd_shader(warp_${mode}_${field} warp.comp -DHALIDE_SAMPLING=${mode} -DHALIDE_MOTION_FIELD=${field})
    endforeach()
    esd_shader(warp_${mode}_residual warp.comp -DHALIDE_SAMPLING=${mode} -DCFA_RESIDUAL_FUSION=1)
endforeach()
cmrc_add_resource_library(esd4d_shaders NAMESPACE esd4d WHENCE "${CMAKE_CURRENT_BINARY_DIR}" ${shaders})
target_compile_definitions(esd4d_shaders INTERFACE ESD4D_ROW_WIDTH=${esd4d_row_width})
# Use the same variant list for embedding and device pipeline warm-up.
file(CONFIGURE OUTPUT "${CMAKE_CURRENT_BINARY_DIR}/esd4d_shader_catalog.inc"
    CONTENT "@catalog@" @ONLY)
target_include_directories(esd4d_shaders INTERFACE "${CMAKE_CURRENT_BINARY_DIR}")

find_program(ESD_SPIRV_DIS spirv-dis REQUIRED NO_CMAKE_FIND_ROOT_PATH)
find_program(ESD_REFLECT_PYTHON python3 REQUIRED NO_CMAKE_FIND_ROOT_PATH)
set(esd_interface "${CMAKE_CURRENT_BINARY_DIR}/EsdShaders.hpp")
set(reflect_esd4d "${CMAKE_CURRENT_SOURCE_DIR}/../esd4d/tools/reflect_esd4d.py")
add_custom_command(OUTPUT "${esd_interface}"
    COMMAND "${ESD_REFLECT_PYTHON}" "${reflect_esd4d}" "${ESD_SPIRV_DIS}"
        "${CMAKE_CURRENT_BINARY_DIR}/esd4d_shader_catalog.inc" "${esd_interface}"
    DEPENDS ${shaders} "${CMAKE_CURRENT_BINARY_DIR}/esd4d_shader_catalog.inc" "${reflect_esd4d}" VERBATIM)
add_custom_target(esd_shader_interface DEPENDS "${esd_interface}")
