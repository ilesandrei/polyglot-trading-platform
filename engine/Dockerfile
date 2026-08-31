# ═══════════════════════════════════════════════════════════════
#  engine/Dockerfile — Multi-stage build for C++ gRPC engine
#
#  Stage 1 (builder): compiles the engine with gRPC inside Linux
#  Stage 2 (runtime): copies only the binary for a lean image
# ═══════════════════════════════════════════════════════════════

# ── Stage 1: Build ───────────────────────────────────────────────
FROM ubuntu:24.04 AS builder

# Install build tools + gRPC/Protobuf dev packages from apt
RUN apt-get update && apt-get install -y --no-install-recommends \
        cmake \
        build-essential \
        libgrpc++-dev \
        libprotobuf-dev \
        protobuf-compiler \
        protobuf-compiler-grpc \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /workspace

# Copy proto definitions
COPY proto/ proto/

# Copy engine source + build config
COPY engine/ engine/

# Build
RUN cmake -S engine -B engine/build \
        -DCMAKE_BUILD_TYPE=Release \
    && cmake --build engine/build --parallel $(nproc)

# ── Stage 2: Runtime ─────────────────────────────────────────────
FROM ubuntu:24.04 AS runtime

# Install runtime shared libraries (same versions as builder)
RUN apt-get update \
    && apt-get install -y --no-install-recommends \
        libgrpc++1.51t64 \
        libprotobuf32t64 \
        ca-certificates \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app
COPY --from=builder /workspace/engine/build/trading_engine .

EXPOSE 50051

ENTRYPOINT ["./trading_engine"]
