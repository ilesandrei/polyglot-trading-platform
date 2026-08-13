# engine/ — C++ Execution Engine

Low-latency Limit Order Book and matching engine (Phase 2).

## Responsibilities
- Receive `SubmitOrder` / `CancelOrder` gRPC calls from the Java Orchestrator
- Run price-time priority matching on the order book
- Stream matched `Trade` objects back to subscribers

## Planned Stack
- **Language:** C++17
- **gRPC:** grpc++ with generated stubs from `proto/`
- **Build:** CMake

> ⚠️ Dockerfile and source code will be added in Phase 2.
