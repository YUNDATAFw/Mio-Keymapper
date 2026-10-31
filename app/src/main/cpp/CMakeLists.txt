cmake_minimum_required(VERSION 3.22.1)

project("mio-native")

add_library(
    mio-native
    SHARED
    native-lib.cpp
)

target_compile_features(mio-native PRIVATE c_std_11 cxx_std_17)

target_link_libraries(
    mio-native
    android
    log
)

add_library(
    secure-string-lib
    SHARED
    string-lib.cpp
)

target_compile_features(secure-string-lib PRIVATE cxx_std_17)

target_link_libraries(
    secure-string-lib
    log
)

add_library(
    integrity-lib
    SHARED
    integrity-lib.cpp
)

target_compile_features(integrity-lib PRIVATE cxx_std_17)

target_link_libraries(
    integrity-lib
    android
    log
    z
)

add_library(
    solstice-ui
    SHARED
    solstice-ui.cpp
    solstice/Arraylist.cpp
    solstice/Notifications.cpp
    solstice/ColorUtils.cpp
    solstice/MathUtils.cpp
    solstice/imgui/imgui.cpp
    solstice/imgui/imgui_draw.cpp
    solstice/imgui/imgui_tables.cpp
    solstice/imgui/imgui_widgets.cpp
    solstice/imgui/backends/imgui_impl_opengl3.cpp
)

target_compile_features(solstice-ui PRIVATE cxx_std_20)

target_compile_definitions(
    solstice-ui
    PRIVATE
    IMGUI_IMPL_OPENGL_ES2
    IMGUI_DISABLE_OBSOLETE_FUNCTIONS
)

target_include_directories(
    solstice-ui
    PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}/solstice/imgui
    ${CMAKE_CURRENT_SOURCE_DIR}/solstice/imgui/backends
)

target_link_libraries(
    solstice-ui
    android
    log
    GLESv3
)

add_library(
    gamepad-uinput
    SHARED
    gamepad_uinput.cpp
)

target_compile_features(gamepad-uinput PRIVATE c_std_11)

target_link_libraries(
    gamepad-uinput
    android
    log
)
