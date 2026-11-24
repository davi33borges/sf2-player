cmake_minimum_required(VERSION 3.22.1)
project(sf2engine CXX)

find_package(oboe REQUIRED CONFIG)

add_library(sf2engine SHARED engine.cpp)
target_compile_features(sf2engine PRIVATE cxx_std_17)
target_compile_options(sf2engine PRIVATE -O3 -ffast-math -Wno-unused-function)
target_link_libraries(sf2engine oboe::oboe log)
