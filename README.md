# Polyglot Trading Platform ⚡

A high-performance, institutional-grade algorithmic and paper-trading platform built with **C++20**, **Java 21**, and **Python 3.11** communicating over **gRPC + Protocol Buffers v3**.

Featuring a **Robinhood-style Live Market & Crypto Paper-Trading Terminal** with real-time price feeds for stocks and 24/7 crypto/memecoins (`ETH`, `BTC`, `SOL`, `DOGE`, `SHIB`, `PEPE`).

---

## 🏛️ System Architecture

```
Python Strategy ──────gRPC (50052)──────► Java Orchestrator (8080) ──────gRPC (50051)──────► C++ Matching Engine
(SMA / RSI Bot)                                │ (Spring Boot 3)                              (OrderBook Depth)
                                               ├── PostgreSQL 16 (Port 5433)
                                               ├── Real-World Live Market Feeds (24/7)
                                               └── Web Trading Terminal (http://localhost:8080/)
```

---

## 💻 Tech Stack & Responsibilities

| Tier | Technology | Key Responsibility |
| :--- | :--- | :--- |
| **Execution Engine** | **C++20**, gRPC, CMake | Low-latency Limit Order Book (FIFO matching, $O(1)$ cancellations, execution streaming). |
| **Orchestrator** | **Java 21**, Spring Boot 3, JPA | Pre-trade risk validation (`RiskEngine`), transactional accounting (`PortfolioService`), real-time market feeds (`MarketDataService`), and REST/Swagger API. |
| **Quant Strategy** | **Python 3.11**, Pandas, NumPy | Automated market data ingestion, Fast/Slow SMA crossover, and Wilder's RSI mean-reversion signals. |
| **Live Market Feeds** | Java 21, 4s TTL Cache | Zero-config exchange chart data for equities (`AAPL`, `NVDA`, `TSLA`, `MSFT`) and 24/7 crypto (`ETH`, `BTC`, `SOL`, `DOGE`, `SHIB`, `PEPE`). |
| **Trading Terminal** | HTML5, Vanilla CSS3 & JS | Institutional dark-mode dashboard with asset catalog, ticker search, live hero banner, order book visualizer, and dynamic PnL tracking. |
| **Database** | **PostgreSQL 16** | System of record for users, portfolios, positions, order lifecycles, and executed trades. |
| **Contracts** | **Protobuf v3** | Canonical domain types and cross-service RPC contracts. |

---

## 🚀 Quick Start

### 1. Launch Core Infrastructure (Docker)
From repository root:
```powershell
docker compose up -d
```
*(Starts `trading-postgres` on port `5433` and `trading-cpp-engine` on port `50051`)*

### 2. Launch Java Orchestrator
```powershell
cd orchestrator
.\gradlew bootRun
```

### 3. Open the Web Terminal
Open your web browser to:
- **Web Trading Terminal**: [http://localhost:8080/](http://localhost:8080/)
- **Swagger UI**: [http://localhost:8080/swagger-ui/index.html](http://localhost:8080/swagger-ui/index.html)

---

## 📖 Documentation Index

- **[`ARCHITECTURE.md`](ARCHITECTURE.md)** — Architectural design, system diagrams, risk rules, and end-to-end sequence flows.
- **[`COMMANDS.md`](COMMANDS.md)** — Comprehensive command reference, PowerShell / cURL examples, database queries, and gRPC testing.
- **[`PROGRESS.md`](PROGRESS.md)** — Detailed progress log across all 6 completed development phases.
- **[`polyglot_trading_roadmap.md`](polyglot_trading_roadmap.md)** — Completed roadmap from architecture design to live paper trading.
- **[`orchestrator/README.md`](orchestrator/README.md)** — Java Spring Boot service documentation.
- **[`engine/README.md`](engine/README.md)** — C++ matching engine documentation.
- **[`strategy/README.md`](strategy/README.md)** — Python quantitative strategy bot documentation.
