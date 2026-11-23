#
# Created by eszdman on 19.11.2026.
#

string(RANDOM LENGTH 16 suffix)
set(directory "${CMAKE_CURRENT_BINARY_DIR}/pipeline-cache-test-${suffix}")
function(check_warmup expected)
    execute_process(COMMAND "${RUNNER}" --prepare-pipelines "${directory}"
        OUTPUT_VARIABLE output RESULT_VARIABLE result)
    if(NOT result EQUAL 0 OR NOT output MATCHES "Prepared ${expected} pipelines")
        message(FATAL_ERROR "Unexpected warm-up result: ${result}\n${output}")
    endif()
endfunction()

check_warmup("[1-9][0-9]*")
check_warmup("0")
file(GLOB cache "${directory}/*.bin")
file(WRITE "${cache}" "truncated")
check_warmup("[1-9][0-9]*")
check_warmup("0")
file(REMOVE_RECURSE "${directory}")
