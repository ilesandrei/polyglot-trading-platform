# strategy/ — Python Quant Strategy Engine

Market data ingestion and automated trading signal generation (Phase 4).

## Responsibilities
- Fetch historical and live market data (Yahoo Finance, CCXT, or mock ticker)
- Run quantitative strategies (Moving Average Crossover, RSI thresholds)
- Send buy/sell signals to the Java Orchestrator via gRPC

## Planned Stack
- **Language:** Python 3.12
- **Libraries:** `pandas`, `numpy`, `grpcio`, `yfinance` / `ccxt`
- **gRPC:** grpcio with generated stubs from `proto/`

> ⚠️ Dockerfile and source code will be added in Phase 4.
