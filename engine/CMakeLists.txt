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

# All generated files go into a dedicated directory in the build tree
set(GENERATED_DIR "${CMAKE_CURRENT_BINARY_DIR}/generated")
file(MAKE_DIRECTORY ${GENERATED_DIR})

set(ALL_GENERATED_SRCS)

foreach(PROTO_FILE ${PROTO_FILES})
    get_filename_component(PROTO_NAME ${PROTO_FILE} NAME_WE)  # e.g. "trading"

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

# ─── Engine Executable ────────────────────────────────────────────
add_executable(trading_engine
    src/order_book.cpp
    src/matching_engine.cpp
    src/grpc_server.cpp
    ${ALL_GENERATED_SRCS}        # generated pb.cc + grpc.pb.cc
)

target_include_directories(trading_engine
    PRIVATE
    src                          # order.hpp, order_book.hpp, matching_engine.hpp
    ${GENERATED_DIR}             # generated .pb.h and .grpc.pb.h
    ${Protobuf_INCLUDE_DIRS}
)

target_link_libraries(trading_engine
    PRIVATE
    Threads::Threads
    protobuf::libprotobuf
    PkgConfig::GRPC              # links grpc++ from apt
)
