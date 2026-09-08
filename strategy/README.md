# strategy/ — Python Quantitative Strategy Engine

Automated market data ingestion and trading signal generation (Phase 4).

---

## 1. Directory Structure

```text
strategy/
├── Dockerfile                  # Containerized deployment
├── README.md                   # Documentation & guide
├── requirements.txt            # Python dependencies (pandas, numpy, grpcio, yfinance)
├── generate_proto.py           # Script to compile ../proto into src/proto/
└── src/
    ├── client/
    │   ├── __init__.py
    │   └── orchestrator_client.py  # gRPC client to Java Orchestrator (port 50052)
    ├── data/
    │   ├── __init__.py
    │   └── market_data.py          # OHLCV fetcher & simulated tick generator
    ├── strategies/
    │   ├── __init__.py
    │   ├── base_strategy.py        # BaseStrategy interface & TradeSignal dataclass
    │   ├── ma_crossover.py         # Moving Average Crossover (Fast/Slow SMA)
    │   └── rsi_strategy.py         # Relative Strength Index (RSI) mean-reversion
    └── main.py                     # Main bot loop orchestrating data -> strategy -> order
```

---

## 2. Local Setup & Installation

### Step 1: Create and Activate Virtual Environment
From the `strategy/` directory:
```powershell
python -m venv .venv
.\.venv\Scripts\Activate.ps1
```

### Step 2: Install Dependencies
```powershell
pip install -r requirements.txt
```

### Step 3: Compile Protobuf Definitions
Compile the shared contracts from `proto/` into `src/proto/`:
```powershell
python generate_proto.py
```

---

## 3. Implementation Guide (TODOs)

The classes and method signatures are pre-defined with step-by-step `TODO` instructions for manual implementation:

1. **Market Data Feed** ([`src/data/market_data.py`](src/data/market_data.py)):
   - `fetch_historical()`: Download OHLCV data with `yfinance` or synthetic bars.
   - `generate_mock_tick()`: Random-walk price simulation.

2. **MA Crossover Strategy** ([`src/strategies/ma_crossover.py`](src/strategies/ma_crossover.py)):
   - `calculate_indicators()`: Compute rolling `fast_sma` and `slow_sma`.
   - `generate_signal()`: Detect Golden Cross (BUY) and Death Cross (SELL).

3. **RSI Strategy** ([`src/strategies/rsi_strategy.py`](src/strategies/rsi_strategy.py)):
   - `calculate_rsi()`: Calculate price deltas, RS, and 14-period RSI.
   - `generate_signal()`: Identify oversold (< 30) and overbought (> 70) regimes.

4. **Orchestrator gRPC Client** ([`src/client/orchestrator_client.py`](src/client/orchestrator_client.py)):
   - `connect()`: Connect to Java Orchestrator (`localhost:50052`).
   - `place_order()`: Map signals to protobuf `Order` and call `PlaceOrder`.
   - `get_portfolio()`: Fetch current cash and position holdings.

5. **Main Bot Loop** ([`src/main.py`](src/main.py)):
   - Connect the data feed, strategy, and client into an automated periodic cycle.

---

## 4. Running the Strategy Bot

Once the methods are implemented:
```powershell
# Set environment variables (optional overrides):
$env:TRADING_SYMBOL = "MSFT"
$env:TRADING_STRATEGY = "MA_CROSSOVER"
$env:USE_MOCK_DATA = "true"

python src/main.py
```
