cmake_minimum_required(VERSION 3.10)
project(serial_port)

add_library(serial_port SHARED serial_port.cpp)

find_library(log-lib log)

target_link_libraries(serial_port ${log-lib})
