# orchestrator/ — Java Orchestrator

Spring Boot central hub for risk management, routing, and the user-facing API (Phase 3).

## Responsibilities
- Expose REST and WebSocket endpoints for users
- Validate orders against user balances (risk engine)
- Forward validated orders to the C++ engine via gRPC
- Persist portfolio and trade state to PostgreSQL
- Consume trade confirmations and update positions

## Planned Stack
- **Language:** Java 21
- **Framework:** Spring Boot 3 (Web, Data JPA, WebSocket)
- **gRPC:** grpc-java with generated stubs from `proto/`
- **DB:** PostgreSQL via Spring Data JPA

> ⚠️ Dockerfile and source code will be added in Phase 3.
