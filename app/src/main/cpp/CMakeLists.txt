cmake_minimum_required(VERSION 3.4.1)

add_definitions(-DAPI_KEY="eyJhbGciOiJIUzI1NiJ9.eyJhdWQiOiIzZjVlYWQ5OTIzNmRhNGU1YzUyNjA1NTJjYzI5NTQzYyIsIm5iZiI6MTY0OTcwMTIwMy44ODk5OTk5LCJzdWIiOiI2MjU0NzE1MzY3ZTBmNzM5YzFhMjIyMzQiLCJzY29wZXMiOlsiYXBpX3JlYWQiXSwidmVyc2lvbiI6MX0.s6vYAqE1eUVxpVRVRoG6BGwiq8BuI9mf0tBHCiCDl7s") # API key shu yerda beriladi

add_library(native-lib SHARED native-lib.cpp)

find_library(log-lib log)

target_link_libraries(native-lib ${log-lib})
