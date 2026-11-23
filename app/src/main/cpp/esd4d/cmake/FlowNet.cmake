#
# Created by eszdman on 19.11.2026.
#

if(NOT ESD4D_FLOWNET)
    return()
endif()
set(layers "${CMAKE_CURRENT_SOURCE_DIR}/../flownet/layer")
add_library(esd4d_flownet_layers STATIC "${layers}/flownetupsample.cpp"
    "${layers}/flownetcorrlookup.cpp" "${layers}/flownetcorrfused.cpp")
target_include_directories(esd4d_flownet_layers PUBLIC "${layers}")
target_link_libraries(esd4d_flownet_layers PUBLIC esd4d_ncnn)
if(ANDROID)
    target_compile_options(esd4d_flownet_layers PRIVATE -fno-rtti -fopenmp)
endif()
target_sources(photon_esd4d PRIVATE nodes/FlowNet.cpp)
target_link_libraries(photon_esd4d PRIVATE esd4d_flownet_layers)
target_compile_definitions(photon_esd4d PUBLIC ESD4D_HAS_FLOWNET=1)
