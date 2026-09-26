cmake_minimum_required(VERSION 3.22.1)
project("shukra")

add_library(
    shukra
    SHARED
    shukra_native.c
    anti_frida.c
    legacy_hash.c
    debug_stub.c
    compat_layer.c
)

# Link against Android log and dl libraries
find_library(log-lib log)
find_library(dl-lib dl)

target_link_libraries(
    shukra
    ${log-lib}
    ${dl-lib}
)

# Hide all symbols except JNI_OnLoad
# This ensures xv1, xv2, xv3 are NOT exported and invisible to nm -D
target_compile_options(shukra PRIVATE
    -fvisibility=hidden
    -O2
    -fstack-protector-strong
)

set_target_properties(shukra PROPERTIES
    C_VISIBILITY_PRESET hidden
    VISIBILITY_INLINES_HIDDEN ON
)
