cmake_minimum_required(VERSION 3.4.1)

add_definitions(-DAPI_KEY="eyJhbGciOiJIUzI1NiJ9.eyJhdWQiOiI5ZTdkNmQ1MWJkNDk3Y2IwYjAwYmExZTg3NThkZDI5NyIsIm5iZiI6MTc2MTM4MDUwMC4yNDgsInN1YiI6IjY4ZmM4ODk0ZGNhYTQ1NWNmMDlmYTdkMyIsInNjb3BlcyI6WyJhcGlfcmVhZCJdLCJ2ZXJzaW9uIjoxfQ.NvIFHc4QnhjROWs3jWWbRHWkibrBa_vxalXOYcbJhtQ") # API key shu yerda beriladi

add_library(native-lib SHARED native-lib.cpp)

find_library(log-lib log)

target_link_libraries(native-lib ${log-lib})
