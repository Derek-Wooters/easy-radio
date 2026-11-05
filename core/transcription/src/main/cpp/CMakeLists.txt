cmake_minimum_required(VERSION 3.22.1)
project(whisper_jni)

# Library only -- no CLI/server/tests from the vendored whisper.cpp tree.
set(WHISPER_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_SERVER OFF CACHE BOOL "" FORCE)
set(GGML_NATIVE OFF CACHE BOOL "" FORCE)
# GGML_NATIVE OFF alone leaves ARM builds with no -march flag at all (cross-compiling means
# there's no host CPU to detect), so ggml's quantized matmul kernels -- which need the
# dotprod SIMD instruction for fast int8 dot products -- silently fall back to slow generic
# code. Confirmed via /proc/cpuinfo ("asimddp" present, "i8mm" absent) that the Pixel 6's
# Tensor G1 (Cortex-A55/A76/X1, all ARMv8.2-A) supports dotprod but not the newer i8mm.
set(GGML_CPU_ARM_ARCH "armv8.2-a+dotprod" CACHE STRING "" FORCE)
set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)

add_subdirectory(whisper.cpp)

add_library(whisper_jni SHARED jni_bridge.cpp)

target_link_libraries(whisper_jni PRIVATE whisper log)
target_include_directories(whisper_jni PRIVATE whisper.cpp/include whisper.cpp/ggml/include)
# Required for Android 15+ devices with a 16KB memory page size -- confirmed on-device via the
# "Android App Compatibility" LOAD-segment-alignment warning before this flag was added.
target_link_options(whisper_jni PRIVATE "-Wl,-z,max-page-size=16384")
