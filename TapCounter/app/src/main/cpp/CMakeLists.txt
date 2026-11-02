cmake_minimum_required(VERSION 3.22.1)
project(tapgesture LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

add_library(tapgesture SHARED gesture_engine.cpp jni_bridge.cpp)
target_compile_options(tapgesture PRIVATE -Wall -Wextra)
