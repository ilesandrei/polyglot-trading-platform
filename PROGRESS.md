# Polyglot Trading Platform — Progress & Roadmap

> Last updated: 2026-09-01

---

## ✅ What Has Been Built

### Phase 1 — Architecture & Data Contracts *(Complete)*

The entire communication foundation of the platform was established.

#### Protobuf Schemas — `proto/`
All shared data structures and gRPC service contracts are defined in two files:

- **[`trading.proto`](proto/trading.proto)** — Core domain types:
  - `Order` — buy/sell request with side, type, quantity, price, status
  - `Trade` — result of a matched pair of orders
  - `Signal` — quant strategy output (action, strength, suggested price/qty)
  - `Portfolio` + `Position` — user holdings snapshot

- **[`services.proto`](proto/services.proto)** — Three gRPC service contracts:
  - `ExecutionService` (C++ server) — SubmitOrder, CancelOrder, StreamExecutions
  - `OrchestratorService` (Java server) — PlaceOrder, GetPortfolio, StreamTrades
  - `StrategyService` (Python server) — StreamSignals, SubmitSignal

#### Database Schema — `infra/db/init.sql`
PostgreSQL tables fully designed and auto-applied on container startup:
- `users` — authentication identity
- `portfolios` — per-user cash balance
- `positions` — per-user per-symbol holdings (quantity, average cost)
- `orders` — full order lifecycle history
- `trades` — every matched execution record
- Indexes on hot query paths (user_id, symbol, status, executed_at)
- Seed: demo user with $100,000 starting balance

#### Infrastructure — `docker-compose.yml`
Complete Docker Compose file orchestrating:
- PostgreSQL 16 with health check
- Zookeeper + Kafka 7.6 (for optional async signaling)
- Placeholder services for all three app layers (behind `--profile full` flag)

---

### Phase 2 — C++ Execution Engine *(Complete)*

A fully working, containerized, low-latency order matching engine accessible over gRPC.

#### Core Matching Logic — `engine/src/`

| File | Responsibility |
|---|---|
| [`order.hpp`](engine/src/order.hpp) | `Order` struct with `Side`, `Type`, `Status` enums |
| [`order_book.hpp/cpp`](engine/src/order_book.cpp) | Per-symbol Limit Order Book — price-time priority (FIFO within price levels) |
| [`matching_engine.hpp/cpp`](engine/src/matching_engine.cpp) | Top-level engine — owns one `OrderBook` per symbol, fires `TradeCallback` on match |
| [`grpc_server.cpp`](engine/src/grpc_server.cpp) | gRPC entry point — wraps engine in `ExecutionService` server |

#### Matching Algorithm
- **Price-time priority**: orders at the same price are filled oldest-first (FIFO)
- BUY orders match against the **lowest available ask**
- SELL orders match against the **highest available bid**
- **Partial fills**: one incoming order can match across multiple resting orders
- **Market orders**: always match, cancelled if no liquidity remains
- **Limit orders**: rest in the book if not immediately filled
- **O(1) cancel**: direct iterator stored in `order_index_` hash map

#### gRPC Server — `engine/src/grpc_server.cpp`
- `SubmitOrder` — deserializes proto → runs `process_order()` → returns status
- `CancelOrder` — scans all books for the order_id, cancels if found
- `StreamExecutions` — registers a `TradeBroadcaster` callback, streams all matched trades in real-time to connected clients
- Thread-safe trade broadcasting via `std::mutex`-protected writer registry

#### Docker Build — `engine/Dockerfile`
Two-stage build:
1. **Builder**: `ubuntu:24.04` + `libgrpc++-dev` + `protobuf-compiler-grpc`
   - CMake runs `protoc` to generate C++ stubs from `.proto` files
   - Compiles all sources into a single binary
2. **Runtime**: `ubuntu:24.04` + runtime shared libs only
   - Copies the binary — lean production image

#### Verified Working
```
$ docker compose build cpp-engine       # ✅ Builds successfully
$ docker compose --profile full up cpp-engine
trading-cpp-engine | [ENGINE] gRPC server listening on 0.0.0.0:50051

# Live gRPC call:
$ grpcurl ... trading.ExecutionService/SubmitOrder
{
  "orderId": "TEST-1",
  "status": "PENDING",
  "message": "Order processed"
}

trading-cpp-engine | [ORDER] Received AAPL BUY qty=5 @ 100   ✅
```

---

### Phase 3 — Java Orchestrator *(Complete)*

The Java Spring Boot orchestrator layer is fully implemented, container-ready, and tested end-to-end. It bridges the REST/gRPC client surface, PostgreSQL state persistence, pre-trade risk controls, and low-latency C++ order book execution.

#### Gradle & Build Configuration
- **[`build.gradle`](orchestrator/build.gradle)**:
  - Spring Boot 3 (Web, Data JPA, WebSocket, Validation)
  - `net.devh:grpc-server-spring-boot-starter` — auto-wires gRPC server on port `50052`
  - `net.devh:grpc-client-spring-boot-starter` — injects managed channel to C++ engine (`localhost:50051`)
  - `org.springdoc:springdoc-openapi-starter-webmvc-ui:2.5.0` — OpenAPI 3 interactive Swagger UI at `/swagger-ui/index.html`
  - `com.google.protobuf` Gradle plugin — runs `protoc` on `proto/` to generate Java stubs
  - `javax.annotation-api:1.3.2` — required for gRPC stubs on Java 9+
- **Gradle wrapper** upgraded to `8.10.2` for JDK 22 support.

#### JPA Entities & Repositories
- **Entities**: [`Order`](orchestrator/src/main/java/com/trading/entity/Order.java), [`Trade`](orchestrator/src/main/java/com/trading/entity/Trade.java), [`Portfolio`](orchestrator/src/main/java/com/trading/entity/Portfolio.java), [`Position`](orchestrator/src/main/java/com/trading/entity/Position.java).
- **Repositories**: [`OrderRepository`](orchestrator/src/main/java/com/trading/repository/OrderRepository.java), [`TradeRepository`](orchestrator/src/main/java/com/trading/repository/TradeRepository.java), [`PortfolioRepository`](orchestrator/src/main/java/com/trading/repository/PortfolioRepository.java), [`PositionRepository`](orchestrator/src/main/java/com/trading/repository/PositionRepository.java).

#### Core Service Implementations
- **[`RiskEngine.java`](orchestrator/src/main/java/com/trading/service/RiskEngine.java)**:
  - `validateBuy()`: Checks user portfolio for sufficient available cash (`cash >= qty * price`).
  - `validateSell()`: Checks user position for sufficient asset quantity (`holding >= qty`).
- **[`PortfolioService.java`](orchestrator/src/main/java/com/trading/service/PortfolioService.java)**:
  - `applyTrade()` with atomic `@Transactional` boundary across buyer and seller.
  - `applyBuy()`: Deducts cash, increments position quantity, and recalculates weighted average cost basis (`(oldCost * oldQty + tradeCost * tradeQty) / newQty`).
  - `applySell()`: Debits position quantity and credits cash to seller portfolio.

#### gRPC & Streaming Layer
- **[`OrchestratorServiceImpl.java`](orchestrator/src/main/java/com/trading/grpc/OrchestratorServiceImpl.java)**:
  - Implements `PlaceOrder`, `GetPortfolio`, `StreamTrades` gRPC server on port `50052`.
  - Enforces pre-trade risk validation, persists orders in PostgreSQL, forwards validated orders to C++ engine via gRPC stub.
- **[`TradeConsumer.java`](orchestrator/src/main/java/com/trading/grpc/TradeConsumer.java)**:
  - Starts background subscription to C++ `StreamExecutions` upon `@PostConstruct`.
  - Persists executed trades in PostgreSQL, updates buyer/seller order statuses to `FILLED`, and invokes `PortfolioService.applyTrade()`.

#### REST API & Swagger UI
- **[`TradingRestController.java`](orchestrator/src/main/java/com/trading/rest/TradingRestController.java)**:
  - `POST /api/orders` — Submits order, validates risk, saves to DB, forwards to C++ engine.
  - `GET /api/orders?userId=` — Returns all orders for a user.
  - `DELETE /api/orders/{orderId}` — Cancels open order via C++ matching engine.
  - `GET /api/portfolio/{userId}` — Returns user cash balance and current holdings.
  - `GET /api/trades?symbol=` — Returns trade execution history.
  - Fully annotated with OpenAPI `@Operation`, `@ApiResponse`, and `@Parameter`. Swagger UI available at `http://localhost:8080/swagger-ui/index.html`.

#### C++ Engine Updates
- Fixed [`matching_engine.cpp`](engine/src/matching_engine.cpp) to generate unique UUID `trade_id` and assign `timestamp_ms` for every executed trade.

#### Verification & End-to-End Testing
- **[`scripts/test_platform.ps1`](scripts/test_platform.ps1)** + **[`scripts/seed_test.sql`](scripts/seed_test.sql)**:
  - Automated 9-step E2E integration test suite covering portfolio queries, risk rejection (cash & holdings), resting limit orders, cancellations, crossing order execution, trade streaming persistence, and portfolio cost-basis updates.
  - **Result**: All 9/9 tests pass (100% functional).

---

### Phase 4 — Python Strategy Engine *(Next)*

Autonomous signal generation from market data.

- [ ] Data ingestion scripts (Yahoo Finance via `yfinance`, or mock live ticker)
- [ ] Implement at least one strategy using `pandas` + `numpy`:
  - Moving Average Crossover (fast MA crosses above slow MA → BUY signal)
  - RSI threshold (RSI < 30 → oversold → BUY, RSI > 70 → overbought → SELL)
- [ ] Python gRPC client to call `OrchestratorService/PlaceOrder` with generated signals
- [ ] Write `strategy/Dockerfile`

---

### Phase 5 — End-to-End Integration *(Weeks 11–12)*

- [ ] `docker compose --profile full up` — all 6 services running together
- [ ] Integration test: Python signal → Java risk check → C++ match → PostgreSQL record
- [ ] Optional: Simple web dashboard (React or Vue) to visualize:
  - Live order book depth
  - Portfolio performance over time
  - Trade execution history

---

## ❓ Open Question: Should We Write Tests?

**Short answer: Yes — and the good news is each layer has a natural testing style.**

### C++ Engine — Unit Tests (highest priority)
The matching engine is the most critical and most testable component. Pure logic with no I/O.

**Recommended: [Google Test (gtest)](https://github.com/google/googletest)**

Example tests to write:
```cpp
TEST(OrderBook, BuyMatchesBestAsk)          // basic match
TEST(OrderBook, PartialFillRemainsInBook)   // resting order updates
TEST(OrderBook, MarketOrderCancelledIfEmpty) // no liquidity
TEST(OrderBook, CancelRemovesFromBook)      // O(1) cancel
TEST(MatchingEngine, PriceTimePriority)     // FIFO within price level
TEST(MatchingEngine, MultiLevelFill)        // sweeps across price levels
```

These run in milliseconds and catch regressions immediately.

### Java Orchestrator — Integration Tests
**Recommended: Spring Boot Test + Testcontainers**
- Spin up a real PostgreSQL container in tests
- Mock the C++ gRPC client with a stub
- Test risk logic, portfolio updates, REST endpoints

### Python Strategy — Unit Tests
**Recommended: `pytest`**
- Feed historical OHLCV data into the strategy functions
- Assert correct BUY/SELL/HOLD signals at known crossover points

### What's the trade-off?
| | Write tests now | Skip tests for now |
|---|---|---|
| **Pros** | Catch bugs as you add gRPC layer; gtest integrates with CMake | Faster to reach Phase 3 |
| **Cons** | ~2 days of extra work upfront | Harder to refactor safely later; bugs get buried under more code |

**Recommendation**: Add C++ unit tests for the matching engine now (before Phase 3), since the engine logic is complete and stable. Java and Python tests can be added alongside their respective phases.
