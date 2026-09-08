# Algorithmic Trading Platform: Polyglot Implementation Roadmap

This roadmap guides you through building a high-performance, polyglot algorithmic trading simulator using **C++**, **Java**, and **Python**.

## Phase 1: Architecture & Data Contracts (Weeks 1-2) ✅
**Goal:** Establish the communication protocols and development environment.
*   [x] **Design the Protobuf Schema:** Create `.proto` files defining the core data structures (`Order`, `Trade`, `Signal`) and gRPC services.
*   [x] **Infrastructure Setup:** Create a `docker-compose.yml` to orchestrate PostgreSQL, Kafka/RabbitMQ (optional), and placeholders for the 3 language services.
*   [x] **Database Schema:** Design the SQL tables for Users, Portfolios, Positions, Orders, and Trade History.

## Phase 2: C++ Execution Engine (Weeks 3-5) ✅
**Goal:** Build a low-latency Limit Order Book (LOB) and matching engine.
*   [x] **Core Data Structures:** Implement `Order` and `OrderBook` classes using standard template library containers (`std::map`, `std::list`).
*   [x] **Matching Logic:** Implement price-time priority matching. Handle partial fills and order cancellations with $O(1)$ fast lookup index.
*   [x] **gRPC Server:** Wrap the engine in a C++ gRPC server to receive `SubmitOrder` requests and stream back execution results (`StreamExecutions`).

## Phase 3: Java Orchestrator & API (Weeks 6-8) ✅
**Goal:** Handle state, risk management, and act as the central hub.
*   [x] **Spring Boot Setup:** Initialize the project with Spring Web, Data JPA, and PostgreSQL drivers on Java 21.
*   [x] **Risk Engine:** Write logic to check user balances, validate order sizes, and update portfolios before forwarding orders.
*   [x] **gRPC Client:** Implement the Java gRPC client to send validated orders to the C++ engine (`ExecutionServiceGrpc`).
*   [x] **REST & OpenAPI:** Expose endpoints for users to view balances, place orders, cancel orders, and inspect trade history via interactive Swagger UI.

## Phase 4: Python Quantitative Strategy (Weeks 9-10) ✅
**Goal:** Ingest market data and generate automated trading signals.
*   [x] **Data Ingestion:** Fetch historical OHLCV data via `yfinance` and generate mock tick streams for offline testing.
*   [x] **Signal Generation:** Implement Moving Average Crossover (Fast/Slow SMA) and Wilder's RSI mean-reversion with `pandas` and `numpy`.
*   [x] **Integration:** Implement Python gRPC client calling Java Orchestrator's `PlaceOrder` and `GetPortfolio` endpoints.

## Phase 5: End-to-End Integration & Web Terminal (Weeks 11-12) ✅
**Goal:** Tie everything together and build real-time visual interface.
*   [x] **Containerization:** Multi-stage slim `Dockerfile`s for C++, Java, and Python services in `docker-compose.yml`.
*   [x] **Integration Testing:** Automated 9-step end-to-end integration test runner (`scripts/test_platform.ps1`) verifying all tiers.
*   [x] **Frontend Terminal:** Modern dark-mode web dashboard (`index.html`, `styles.css`, `app.js`) with live Order Book depth, Trader switching, active orders, and trade feed.

## Phase 6: Live Market & Crypto Paper-Trading Engine (Weeks 13-14) ✅
**Goal:** Upgrade to real-world live market prices and 24/7 crypto paper trading.
*   [x] **Live Market Data Feed:** Built `MarketDataService.java` with 4-second TTL in-memory caching connecting to real exchange feeds without API keys.
*   [x] **Instant Market Fills:** Single-user paper trading endpoint (`POST /api/market/instant-order`) executing immediately at live prices against automated exchange liquidity.
*   [x] **Robinhood Crypto & Stock Catalog:** Interactive category tabs (`All`, `Crypto & Memecoins`, `US Equities`) with live price chips (`ETH`, `BTC`, `SOL`, `DOGE`, `SHIB`, `PEPE`, `NVDA`, `TSLA`, `AAPL`, `MSFT`).
*   [x] **Ticker Search Bar:** Real-time ticker search for any equity or coin symbol.
*   [x] **Live Portfolio P&L Tracking:** Real-time calculation of unrealized profit/loss ($ and %) across held assets with 1-click **Sell** action.
