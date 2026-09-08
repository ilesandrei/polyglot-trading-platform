# Polyglot Trading Platform — Progress & Roadmap

> Last updated: 2026-09-08

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

### Phase 4 — Python Quantitative Strategy Engine *(Complete)*

The Python algorithmic trading engine in `strategy/` is fully implemented, verified, and integrated with the Java Orchestrator via gRPC.

#### Implemented Components
- **[`requirements.txt`](strategy/requirements.txt)**: `grpcio`, `grpcio-tools`, `protobuf`, `pandas`, `numpy`, `yfinance`.
- **[`generate_proto.py`](strategy/generate_proto.py)**: Compiles `trading.proto` and `services.proto` into `src/proto/` with relative import patching.
- **[`market_data.py`](strategy/src/data/market_data.py)**: Market data feed with dual sources:
  - Real-world OHLCV historical candle download via `yfinance`.
  - Synthetic random-walk bar and tick generator for offline / after-hours testing.
- **[`ma_crossover.py`](strategy/src/strategies/ma_crossover.py)**: Moving Average Crossover (Fast & Slow SMA):
  - Golden Cross $\to$ `BUY` signal.
  - Death Cross $\to$ `SELL` signal.
- **[`rsi_strategy.py`](strategy/src/strategies/rsi_strategy.py)**: Relative Strength Index mean-reversion:
  - Exponential moving average Wilder's RSI calculation.
  - Oversold (< 30) $\to$ `BUY` signal.
  - Overbought (> 70) $\to$ `SELL` signal.
- **[`orchestrator_client.py`](strategy/src/client/orchestrator_client.py)**:
  - Connects to Java Orchestrator `OrchestratorService` on port `50052`.
  - Maps internal signals to protobuf `Order` and calls `PlaceOrder`.
  - Fetches live balances and holdings via `GetPortfolio`.
- **[`main.py`](strategy/src/main.py)**:
  - Automated bot runner loop: Ingests market data $\to$ evaluates strategy $\to$ dispatches orders via gRPC.
- **[`Dockerfile`](strategy/Dockerfile)**:
  - Containerized production build for the Python strategy bot.

#### Verified End-to-End
- ✅ Historical OHLCV download & mock tick generation verified.
- ✅ Crossover & RSI signal math verified with unit tests.
- ✅ Live gRPC call from Python $\to$ Java Orchestrator $\to$ PostgreSQL $\to$ C++ engine executed successfully (`PlaceOrder` returned `PENDING`, persisted in `orders` table).

---

### Phase 5 — Full System Integration & Real-Time Web Dashboard *(Complete)*

All tiers of the Polyglot Trading Platform are fully integrated, containerized, and accessible via an institutional-grade real-time web trading terminal.

#### 1. Interactive Web Trading Dashboard (`orchestrator/src/main/resources/static/`)
- **[`index.html`](orchestrator/src/main/resources/static/index.html)**:
  - Accessible directly at `http://localhost:8080/`.
  - Dark mode with Google Font `Inter` and glassmorphic cards.
  - Active trader switching between Buyer (`0000...0001`) and Seller (`0000...0002`).
  - System status indicators for C++ Engine (`50051`), PostgreSQL (`5433`), and Spring Hub (`8080`).
- **[`styles.css`](orchestrator/src/main/resources/static/styles.css)**:
  - Custom institutional dark-mode design system with neon accents, glowing status tags, and micro-animations.
- **[`app.js`](orchestrator/src/main/resources/static/app.js)**:
  - Real-time polling (every 2s) querying balances, holdings, open orders, and trades.
  - **Dynamic Order Book Depth Visualizer**: Real-time Bids (emerald green) vs Asks (crimson) with live spread indicator.
  - **Order Execution Terminal**: Buy/Sell limit orders with quick sizing buttons (25%, 50%, 75%, MAX) and instant toast feedback.
  - **Order Cancellation**: In-flight cancellation of open/resting orders directly from the UI.
  - **Portfolio & Trade Tape**: Live calculation of total equity, asset positions with average cost basis, and recent executions tape.

#### 2. Full Multi-Container Stack (`docker-compose.yml`)
- All services containerized with multi-stage slim production images:
  - `postgres` (PostgreSQL 16)
  - `cpp-engine` (C++20 Matching Engine)
  - `java-orchestrator` (Java 21 / Spring Boot 3 Hub)
  - `python-strategy` (Python 3.11 Quantitative Strategy Bot)

#### 3. Verification & Reliability
- Verified interactive web dashboard in browser with live session recording and screenshot.
- Automated 9-step end-to-end integration test suite passing 9/9 checks.
- Python unit test suite passing 10/10 checks in 35ms.

---

### Phase 6 — Live Market & Crypto Paper-Trading Engine *(Complete)*

The platform was upgraded from a two-party simulation into a **Robinhood-style Live Market Paper Trading Engine** supporting real-world US equities (`NVDA`, `TSLA`, `AAPL`, `MSFT`, `AMZN`) and 24/7 crypto/memecoins (`ETH`, `BTC`, `SOL`, `DOGE`, `SHIB`, `PEPE`).

#### 1. Live Market Feed Service (`orchestrator/src/main/java/com/trading/service/MarketDataService.java`)
- Connects directly to exchange market data feeds without requiring API keys.
- Thread-safe 4-second TTL in-memory caching to eliminate rate limits and provide sub-millisecond local response times.
- Auto-normalizes crypto short-names (`ETH` $\to$ `ETH-USD`, `PEPE` $\to$ `PEPE-USD`).
- Slices real-time quotes: current price, 24h change percent, day high, day low, volume, and company/token name.
- Pre-configured asset catalog of top Robinhood crypto and tech stocks.

#### 2. Instant Paper Market Execution (`POST /api/market/instant-order`)
- Single-user paper trading: immediately fills orders at the real-world live market price against an automated exchange liquidity provider.
- Pre-trade risk enforcement via [`RiskEngine.java`](orchestrator/src/main/java/com/trading/service/RiskEngine.java) calculating required cash for `MARKET` buys based on live market quote estimates.
- Atomic balance and custody settlement via [`PortfolioService.applyPaperTrade()`](orchestrator/src/main/java/com/trading/service/PortfolioService.java).
- Fully maintains relational data integrity with `orders` and `trades` foreign keys in PostgreSQL.

#### 3. Frontend Terminal Upgrades (`orchestrator/src/main/resources/static/`)
- **Asset Catalog Bar & Tabs**: Filter by **All Assets**, **🔥 Crypto & Memecoins**, or **📈 US Equities** with live-updating price chips.
- **Ticker Search Bar**: Search any stock or coin ticker (e.g., `PEPE`, `ETH`, `TSLA`, `DOGE`) to stream quotes and trade immediately.
- **Ticker Hero Banner**: Real-time price display, 24h change badge (`badge-up` / `badge-down`), and 24h day range.
- **Execution Mode Switcher**: Seamless toggle between **⚡ Instant Market Fill (Live Paper)** and **📖 Limit Order (C++ Matching Engine)**.
- **Dynamic Portfolio Holdings & Live P&L**: Every held position continuously recalculates unrealized P&L ($ and %) against live market quotes.
- **1-Click "Sell" Action**: Instant position trimming or closing at the current market price.

#### 4. End-to-End Verification
- ✅ Real-time quote retrieval verified for `ETH-USD` ($2,482.73), `DOGE-USD` ($0.0898), `PEPE-USD` ($0.00000620), and `NVDA` ($106.47).
- ✅ Instant Paper Buy executed for `ETH-USD` (0.5 ETH) and `DOGE-USD` (100 DOGE) with instant PostgreSQL balance updates.
- ✅ Full browser subagent test recording saved: `crypto_paper_trading_demo_1788895694438.webp`.
- ✅ Complete dashboard screenshot captured: `trading_dashboard_1788895795290.png`.

---

### Phase 7 — Interactive Real-Time Candlestick & Price Chart *(Complete)*

The platform was upgraded with an institutional-grade financial chart powered by TradingView Lightweight Charts, with real-world OHLCV historical candle streaming, live candle ticking, multi-timeframe navigation, and technical indicator overlays.

#### 1. Zero-Dependency Chart Engine
- Bundled [`lightweight-charts.standalone.production.js`](orchestrator/src/main/resources/static/lightweight-charts.standalone.production.js) directly inside `static/` (100% offline-ready, zero external CDN dependencies).
- Styled with dark mode aesthetics (transparent slate navy `#0b101d`, emerald wicks `#10b981`, crimson wicks `#f43f5e`, subtle crosshair grid).

#### 2. Live OHLCV Historical Data Streaming
- **Service**: Added `getCandles(symbol, range, interval)` in [`MarketDataService.java`](orchestrator/src/main/java/com/trading/service/MarketDataService.java) querying real market candle data with fallback generators.
- **REST Endpoint**: `GET /api/market/history?symbol={symbol}&range={range}&interval={interval}` in [`TradingRestController.java`](orchestrator/src/main/java/com/trading/rest/TradingRestController.java).
- **TTL Cache**: 15-second cache to prevent rate-limiting while serving rapid timeframe queries.
- **Live Candle Ticking**: Pulse the active candle dynamically as 2.5s live market price ticks arrive without full reloads.

#### 3. Timeframes, Indicators & Controls
- **Timeframes**: `1D` (5m candles), `5D` (15m candles), `1M` (1h candles), and `1Y` (1d candles).
- **Chart Types**: Instant toggle between **Candles** and **Line** (area gradient).
- **Indicators**: **SMA 20** (amber overlay) and **Volume Histogram** (color-coded bars).
- **Interactive Legend**: Crosshair hover displays real-time `O`, `H`, `L`, `C`, and `Vol`.
- **Holding Auto-Clean**: Fully closed positions (quantity = 0) are automatically cleaned from the database in [`PortfolioService.java`](orchestrator/src/main/java/com/trading/service/PortfolioService.java) and removed from the UI.

#### 4. Verification & Testing
- ✅ REST endpoint verified: `curl http://localhost:8080/api/market/history?symbol=ETH-USD&range=1d&interval=5m` returns live timestamped OHLCV candles.
- ✅ Full browser subagent test passed: Timeframe toggling, candle/line toggles, SMA 20/Volume toggling, crosshair hovering, and asset switching (`ETH-USD` $\to$ `NVDA` $\to$ `DOGE-USD`).
- ✅ Browser session recorded: `candlestick_chart_demo_1788897181885.webp`.

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
