cmake_minimum_required(VERSION 3.20)
project(TradingEngine CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# ─── Find Packages ────────────────────────────────────────────────
find_package(Threads REQUIRED)
find_package(Protobuf REQUIRED)
find_package(gRPC CONFIG REQUIRED)

# ─── Proto compilation ────────────────────────────────────────────
set(PROTO_DIR "${CMAKE_SOURCE_DIR}/../proto")
set(PROTO_SRCS
    "${PROTO_DIR}/trading.proto"
    "${PROTO_DIR}/services.proto"
)

add_library(proto_lib STATIC ${PROTO_SRCS})
target_link_libraries(proto_lib PUBLIC protobuf::libprotobuf gRPC::grpc++)
protobuf_generate(TARGET proto_lib LANGUAGE cpp)
grpc_generate_cpp(GRPC_SRCS GRPC_HDRS ${PROTO_DIR} ${PROTO_SRCS})

# ─── Engine Executable ────────────────────────────────────────────
set(ENGINE_SOURCES
    src/main.cpp
    src/order.cpp
    src/order_book.cpp
    src/matching_engine.cpp
    src/grpc_server.cpp
)

add_executable(trading_engine ${ENGINE_SOURCES} ${GRPC_SRCS})
target_include_directories(trading_engine PRIVATE src)
target_link_libraries(trading_engine
    PRIVATE
    proto_lib
    Threads::Threads
    gRPC::grpc++
    protobuf::libprotobuf
)
