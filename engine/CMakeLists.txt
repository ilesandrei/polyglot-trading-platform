cmake_minimum_required(VERSION 3.20)
project(TradingEngine CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# ─── Find Packages ────────────────────────────────────────────────
find_package(Threads REQUIRED)
find_package(Protobuf REQUIRED)           # provides Protobuf_PROTOC_EXECUTABLE
find_package(PkgConfig REQUIRED)
pkg_check_modules(GRPC REQUIRED IMPORTED_TARGET grpc++)   # Ubuntu apt package
find_program(GRPC_CPP_PLUGIN grpc_cpp_plugin REQUIRED)   # /usr/bin/grpc_cpp_plugin

# ─── Proto code generation ────────────────────────────────────────
set(PROTO_DIR "${CMAKE_SOURCE_DIR}/../proto")
set(PROTO_FILES
    "${PROTO_DIR}/trading.proto"
    "${PROTO_DIR}/services.proto"
)

set(GENERATED_DIR "${CMAKE_CURRENT_BINARY_DIR}/generated")
file(MAKE_DIRECTORY ${GENERATED_DIR})

set(ALL_GENERATED_SRCS)

foreach(PROTO_FILE ${PROTO_FILES})
    get_filename_component(PROTO_NAME ${PROTO_FILE} NAME_WE)

    set(PB_CC   "${GENERATED_DIR}/${PROTO_NAME}.pb.cc")
    set(PB_H    "${GENERATED_DIR}/${PROTO_NAME}.pb.h")
    set(GRPC_CC "${GENERATED_DIR}/${PROTO_NAME}.grpc.pb.cc")
    set(GRPC_H  "${GENERATED_DIR}/${PROTO_NAME}.grpc.pb.h")

    add_custom_command(
        OUTPUT  ${PB_CC} ${PB_H} ${GRPC_CC} ${GRPC_H}
        COMMAND ${Protobuf_PROTOC_EXECUTABLE}
        ARGS
            --cpp_out=${GENERATED_DIR}
            --grpc_out=${GENERATED_DIR}
            --plugin=protoc-gen-grpc=${GRPC_CPP_PLUGIN}
            -I${PROTO_DIR}
            ${PROTO_FILE}
        DEPENDS ${PROTO_FILE}
        COMMENT "Generating stubs for ${PROTO_NAME}.proto"
    )

    list(APPEND ALL_GENERATED_SRCS ${PB_CC} ${GRPC_CC})
endforeach()

# ─── Core engine library (shared by server + tests) ───────────────
# grpc_server.cpp is NOT included here — tests don't need gRPC.
add_library(engine_core STATIC
    src/order_book.cpp
    src/matching_engine.cpp
)
target_include_directories(engine_core PUBLIC src)
target_link_libraries(engine_core PUBLIC Threads::Threads)

# ─── gRPC Server Executable ───────────────────────────────────────
add_executable(trading_engine
    src/grpc_server.cpp
    ${ALL_GENERATED_SRCS}
)
target_include_directories(trading_engine
    PRIVATE
    src
    ${GENERATED_DIR}
    ${Protobuf_INCLUDE_DIRS}
)
target_link_libraries(trading_engine
    PRIVATE
    engine_core
    protobuf::libprotobuf
    PkgConfig::GRPC
)

# ─── Google Test ──────────────────────────────────────────────────
include(FetchContent)
FetchContent_Declare(
    googletest
    URL https://github.com/google/googletest/archive/v1.14.0.zip
    DOWNLOAD_EXTRACT_TIMESTAMP ON
)
# Prevent gtest from overriding our compiler/linker settings on Windows
set(gtest_force_shared_crt ON CACHE BOOL "" FORCE)
FetchContent_MakeAvailable(googletest)

# ─── Test Executable ──────────────────────────────────────────────
enable_testing()

add_executable(engine_tests
    tests/test_order_book.cpp
    tests/test_matching_engine.cpp
)
target_link_libraries(engine_tests
    PRIVATE
    engine_core
    GTest::gtest_main   # provides main() + test runner
)

include(GoogleTest)
gtest_discover_tests(engine_tests)   # registers each TEST() with ctest
