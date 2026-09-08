# Orchestrator — Java 21 & Spring Boot Hub

The **Java Orchestrator** is the central nervous system of the Polyglot Trading Platform. It coordinates pre-trade financial risk controls, real-time market data feeds, transactional portfolio accounting, cross-service gRPC communication, and the browser-based web trading terminal.

---

## 🏗️ Architecture & Core Components

```
                     ┌──────────────────────────────┐
                     │  Web Terminal / Browser / UI │
                     └──────────────┬───────────────┘
                                    │ HTTP (Port 8080)
                                    ▼
┌────────────────────────────────────────────────────────────────────────┐
│                        Java Orchestrator Hub                           │
│                                                                        │
│  ┌─────────────────────────┐              ┌─────────────────────────┐  │
│  │  TradingRestController  │              │ OrchestratorServiceImpl │  │
│  │   (REST & Swagger UI)   │              │      (gRPC: 50052)      │  │
│  └────────────┬────────────┘              └────────────┬────────────┘  │
│               │                                        │               │
│               ▼                                        ▼               │
│  ┌─────────────────────────┐              ┌─────────────────────────┐  │
│  │    MarketDataService    │              │       RiskEngine        │  │
│  │ (Live Quotes, 4s Cache) │              │  (Pre-Trade Validation) │  │
│  └────────────┬────────────┘              └────────────┬────────────┘  │
│               │                                        │               │
│               ▼                                        ▼               │
│  ┌─────────────────────────┐              ┌─────────────────────────┐  │
│  │    PortfolioService     │              │     TradeConsumer       │  │
│  │ (@Transactional Settle) │              │ (Subscribes to Engine)  │  │
│  └────────────┬────────────┘              └────────────┬────────────┘  │
└───────────────┼────────────────────────────────────────┼───────────────┘
                │ JDBC (Port 5433)                       │ gRPC (Port 50051)
                ▼                                        ▼
    ┌───────────────────────┐               ┌─────────────────────────┐
    │     PostgreSQL 16     │               │   C++ Matching Engine   │
    └───────────────────────┘               └─────────────────────────┘
```

---

## 📦 Package Structure

```
orchestrator/src/main/java/com/trading/
├── entity/
│   ├── Order.java               # JPA Entity for order lifecycle
│   ├── Trade.java               # JPA Entity for executed matches
│   ├── Portfolio.java           # JPA Entity for user cash balances
│   └── Position.java            # JPA Entity for per-symbol holdings & average cost
├── repository/
│   ├── OrderRepository.java     # DB queries for orders
│   ├── TradeRepository.java     # DB queries for executed trades
│   ├── PortfolioRepository.java # DB queries for user cash balances
│   └── PositionRepository.java  # DB queries for user asset positions
├── service/
│   ├── RiskEngine.java          # Pre-trade buying power & custody checks
│   ├── PortfolioService.java    # Atomic settlement & cost basis calculation
│   └── MarketDataService.java   # Real-time stock & crypto market data feed
├── rest/
│   └── TradingRestController.java # REST endpoints, instant fills, catalog
└── grpc/
    ├── OrchestratorServiceImpl.java # gRPC server on 50052 (PlaceOrder, GetPortfolio)
    └── TradeConsumer.java           # Background stream consumer from C++ engine (50051)
```

---

## ⚡ Key Capabilities

### 1. Live Market Feeds (`MarketDataService.java`)
- Connects directly to exchange chart endpoints for real-world equity & crypto prices.
- Slices: Current Price, 24h Change %, Day High, Day Low, and Volume.
- In-memory thread-safe 4-second TTL cache guarantees `<1ms` responses without rate-limiting.
- Built-in symbol normalization (`ETH` $\to$ `ETH-USD`, `PEPE` $\to$ `PEPE-USD`).

### 2. Dual Execution Modes
1. **⚡ Instant Paper Market Fills (`/api/market/instant-order`)**:
   - Single-user friendly: fills orders immediately at real-world prices without waiting for counterparties.
   - Enforces pre-trade risk checks via `RiskEngine` against real-time estimated values.
2. **📖 Limit Order Book (`/api/orders`)**:
   - Forwards orders via gRPC to the high-performance C++ matching engine.
   - Supports price-time priority matching, resting orders, and $O(1)$ cancellations.

### 3. Pre-Trade Risk Engine (`RiskEngine.java`)
- **BUY Orders**: Confirms user cash covers the order value ($\text{cash} \ge \text{quantity} \times \text{price}$).
- **SELL Orders**: Confirms user holds sufficient shares/tokens ($\text{holding} \ge \text{quantity}$).
- Immediate rejection with HTTP 422 if validation fails.

### 4. Atomic Transactional Accounting (`PortfolioService.java`)
- Handles cash debits/credits and inventory updates within strict `@Transactional` boundaries.
- Continuously recalculates weighted average cost basis:
  $$\text{avgCost}_{\text{new}} = \frac{(\text{avgCost}_{\text{old}} \times \text{qty}_{\text{old}}) + (\text{price} \times \text{quantity})}{\text{qty}_{\text{new}}}$$

---

## 🌐 Web Terminal & Static UI

Frontend static assets are located in [`src/main/resources/static/`](src/main/resources/static/):
- **`index.html`**: Terminal layout, Asset Catalog, Live Ticker Hero Banner, Order Book visualizer, and Portfolio Holdings table.
- **`styles.css`**: Institutional dark-mode design system with neon accents and micro-animations.
- **`app.js`**: Real-time quote streaming, catalog chips, search bar, and live P&L calculations.

Accessible in any web browser at:
- **Trading Terminal**: [http://localhost:8080/](http://localhost:8080/)
- **Swagger UI**: [http://localhost:8080/swagger-ui/index.html](http://localhost:8080/swagger-ui/index.html)
- **OpenAPI JSON**: [http://localhost:8080/v3/api-docs](http://localhost:8080/v3/api-docs)

---

## 🚀 Running the Orchestrator

### Prerequisites
- Java 21 JDK
- PostgreSQL running on port `5433` (via Docker Compose)
- C++ Engine running on port `50051` (via Docker Compose)

### Launch Command
```powershell
cd orchestrator
.\gradlew bootRun
```
