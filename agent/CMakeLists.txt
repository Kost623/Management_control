cmake_minimum_required(VERSION 3.20)
project(management-control-agent VERSION 0.1.0 LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

if(NOT CMAKE_BUILD_TYPE)
    set(CMAKE_BUILD_TYPE Release)
endif()

option(MC_AGENT_BUILD_TESTS "Збирати тести агента" ON)

# --- Залежності ---
find_package(CURL REQUIRED)
find_package(Threads REQUIRED)

add_library(mc_core STATIC
        src/json.cpp
        src/http.cpp
        src/config.cpp
        src/platform.cpp
        src/activity.cpp
        src/agent.cpp
        src/screenshot.cpp
)
target_include_directories(mc_core PUBLIC include)
target_link_libraries(mc_core PUBLIC CURL::libcurl Threads::Threads)

if(WIN32)
    target_sources(mc_core PRIVATE src/platform_win.cpp)
    target_link_libraries(mc_core PUBLIC ole32 gdi32 user32 advapi32 shlwapi psapi)
    target_compile_definitions(mc_core PUBLIC MC_PLATFORM_WINDOWS=1)
else()
    # Заглушка: дозволяє збирати й тестувати переносиму частину на Linux/macOS
    target_sources(mc_core PRIVATE src/platform_stub.cpp)
    target_compile_definitions(mc_core PUBLIC MC_PLATFORM_STUB=1)
endif()

target_compile_options(mc_core PRIVATE
        $<$<CXX_COMPILER_ID:MSVC>:/W4 /permissive- /utf-8>
        $<$<NOT:$<CXX_COMPILER_ID:MSVC>>:-Wall -Wextra>)

add_executable(mc-agent src/main.cpp)
target_link_libraries(mc-agent PRIVATE mc_core)

if(MC_AGENT_BUILD_TESTS)
    enable_testing()
    add_executable(mc_tests tests/test_main.cpp)
    target_link_libraries(mc_tests PRIVATE mc_core)
    add_test(NAME mc_tests COMMAND mc_tests)
endif()

install(TARGETS mc-agent RUNTIME DESTINATION bin)
