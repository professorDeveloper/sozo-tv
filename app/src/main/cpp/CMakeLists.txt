cmake_minimum_required(VERSION 3.4.1)

# Agar API_KEY build vaqtida kiritilmagan bo'lsa bo'sh string qilamiz
if(NOT DEFINED API_KEY)
    set(API_KEY "")
endif()

# API_KEY ni define qilib qo'yamiz (C kompilator uchun -DAPI_KEY="value")
add_definitions(-DAPI_KEY="${API_KEY}")

add_library(native-lib SHARED native-lib.cpp)

find_library(log-lib log)

target_link_libraries(native-lib ${log-lib})
