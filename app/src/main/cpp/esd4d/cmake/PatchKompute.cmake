#
# Created by eszdman on 19.11.2026.
#

# FetchContent may repeat the patch step when reconfiguring an existing build.
# Keep git from discovering a parent PhotonCamera checkout for in-tree builds.
get_filename_component(source_dir "." REALPATH)
get_filename_component(ENV_CEILING "${source_dir}" DIRECTORY)
set(ENV{GIT_CEILING_DIRECTORIES} "${ENV_CEILING}")
string(REPLACE "," ";" patch_names "${PATCH_NAMES}")
foreach(name IN LISTS patch_names)
    set(patch "${CMAKE_CURRENT_LIST_DIR}/kompute-${name}.patch")
    execute_process(COMMAND "${GIT}" apply --reverse --check "${patch}"
                    RESULT_VARIABLE already_applied ERROR_QUIET)
    if(NOT already_applied EQUAL 0)
        execute_process(COMMAND "${GIT}" apply "${patch}" COMMAND_ERROR_IS_FATAL ANY)
    endif()
endforeach()
