# Polyglot Trading Platform — Commands & Runbook

This guide covers all manual commands to build, start, run, test, and inspect every component of the platform.

---

## 1. Quick Start (Standard Workflow)

### Step 1: Start PostgreSQL & C++ Matching Engine
From the repository root:
```powershell
docker compose up -d
```
Verify both containers are healthy:
```powershell
docker ps
```
*(You should see `trading-postgres` on `0.0.0.0:5433->5432` and `trading-cpp-engine` on `0.0.0.0:50051->50051`)*

### Step 2: Start Java Orchestrator
Open a dedicated terminal and run:
```powershell
cd orchestrator
.\gradlew bootRun
```
Wait until you see:
```text
Tomcat started on port 8080 (http) with context path '/'
gRPC Server started, listening on address: *, port: 50052
[CONSUMER] Connected to C++ engine execution stream
```

### Step 3: Run Automated End-to-End Tests
Open a second terminal and run:
```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\test_platform.ps1
```
*(Executes all 9 automated verification steps across the whole system).*

---

## 2. Interactive Web Trading Dashboard & Swagger UI

Once the Java orchestrator is running, open your browser to:

| Interface | URL | Description |
|---|---|---|
| **Web Trading Terminal** | [http://localhost:8080/](http://localhost:8080/) | Live institutional trading dashboard with Depth Order Book, Portfolio & Holdings, Order Placement, and Cancel controls. |
| **Swagger UI** | [http://localhost:8080/swagger-ui/index.html](http://localhost:8080/swagger-ui/index.html) | Interactive OpenAPI 3 endpoint documentation & direct API tester. |
| **OpenAPI Spec (JSON)** | [http://localhost:8080/v3/api-docs](http://localhost:8080/v3/api-docs) | Raw OpenAPI v3 specification. |


### Using Swagger UI:
1. Navigate to [http://localhost:8080/swagger-ui/index.html](http://localhost:8080/swagger-ui/index.html).
2. Click on any endpoint (e.g. `POST /api/orders` or `GET /api/portfolio/{userId}`).
3. Click **"Try it out"**.
4. Use test user IDs:
   - **Buyer User ID**: `00000000-0000-0000-0000-000000000001`
   - **Seller User ID**: `00000000-0000-0000-0000-000000000002`
5. Click **"Execute"** to inspect live request/response payloads.

---

## 3. Manual REST API Commands (PowerShell & cURL)

### A. Check User Portfolio & Holdings
**PowerShell:**
```powershell
Invoke-RestMethod -Uri "http://localhost:8080/api/portfolio/00000000-0000-0000-0000-000000000001" | ConvertTo-Json -Depth 5
```
**cURL:**
```bash
curl -X GET "http://localhost:8080/api/portfolio/00000000-0000-0000-0000-000000000001" -H "Accept: application/json"
```

---

### B. Live Market Quotes & Asset Catalog
#### 1. Fetch Asset Catalog (Stocks & Robinhood Memecoins):
```powershell
Invoke-RestMethod -Uri "http://localhost:8080/api/market/catalog" | Format-Table symbol, name, category, icon
```
#### 2. Query Real-Time Quote for Any Asset (Crypto or Stock):
```powershell
# Ethereum live quote
Invoke-RestMethod -Uri "http://localhost:8080/api/market/quote?symbol=ETH-USD" | ConvertTo-Json

# Dogecoin live quote
Invoke-RestMethod -Uri "http://localhost:8080/api/market/quote?symbol=DOGE-USD" | ConvertTo-Json

# Pepe memecoin live quote
Invoke-RestMethod -Uri "http://localhost:8080/api/market/quote?symbol=PEPE-USD" | ConvertTo-Json

# US Stock live quote (Nvidia)
Invoke-RestMethod -Uri "http://localhost:8080/api/market/quote?symbol=NVDA" | ConvertTo-Json
```

---

### C. Execute Instant Paper Market Order (Live Real-World Fill)
Execute a paper buy or sell at the current real-world market price with instant execution (no waiting for counterparties):
**PowerShell:**
```powershell
$order = @{
    userId   = "00000000-0000-0000-0000-000000000001"
    symbol   = "ETH-USD"
    side     = "BUY"
    quantity = 0.5
} | ConvertTo-Json

Invoke-RestMethod -Uri "http://localhost:8080/api/market/instant-order" -Method Post -Body $order -ContentType "application/json" | ConvertTo-Json
```
**cURL:**
```bash
curl -X POST "http://localhost:8080/api/market/instant-order" \
  -H "Content-Type: application/json" \
  -d '{"userId":"00000000-0000-0000-0000-000000000001","symbol":"ETH-USD","side":"BUY","quantity":0.5}'
```

---

### D. Place a Limit Order into C++ Matching Engine (Resting in Book)
**PowerShell:**
```powershell
$order = @{
    userId   = "00000000-0000-0000-0000-000000000001"
    symbol   = "AAPL"
    side     = "BUY"
    type     = "LIMIT"
    quantity = 10
    price    = 150.00
} | ConvertTo-Json

Invoke-RestMethod -Uri "http://localhost:8080/api/orders" -Method Post -Body $order -ContentType "application/json"
```
**cURL:**
```bash
curl -X POST "http://localhost:8080/api/orders" \
  -H "Content-Type: application/json" \
  -d '{
    "userId": "00000000-0000-0000-0000-000000000001",
    "symbol": "AAPL",
    "side": "BUY",
    "type": "LIMIT",
    "quantity": 10,
    "price": 150.00
  }'
```

---

### E. List All Orders for a User
**PowerShell:**
```powershell
Invoke-RestMethod -Uri "http://localhost:8080/api/orders?userId=00000000-0000-0000-0000-000000000001" | ConvertTo-Json -Depth 5
```
**cURL:**
```bash
curl -X GET "http://localhost:8080/api/orders?userId=00000000-0000-0000-0000-000000000001"
```

---

### F. Cancel an Open Order
**PowerShell:**
```powershell
Invoke-RestMethod -Uri "http://localhost:8080/api/orders/<ORDER_UUID>" -Method Delete
```
**cURL:**
```bash
curl -X DELETE "http://localhost:8080/api/orders/<ORDER_UUID>"
```

---

### G. View Trade Execution History by Symbol
**PowerShell:**
```powershell
Invoke-RestMethod -Uri "http://localhost:8080/api/trades?symbol=AAPL" | ConvertTo-Json -Depth 5
```
**cURL:**
```bash
curl -X GET "http://localhost:8080/api/trades?symbol=AAPL"
```

---

## 4. Direct Database Commands (PostgreSQL)

The database runs in Docker on port `5433` (`trading_db`, user `trading_user`).

### Connect via psql in Docker:
```powershell
docker exec -it trading-postgres psql -U trading_user -d trading_db
```

### Useful SQL Queries:
```sql
-- View all portfolios and cash balances
SELECT * FROM portfolios;

-- View all positions / holdings
SELECT * FROM positions;

-- View recent orders
SELECT id, user_id, symbol, side, type, quantity, price, status, created_at 
FROM orders 
ORDER BY created_at DESC 
LIMIT 10;

-- View executed trades
SELECT id, symbol, quantity, price, buy_order_id, sell_order_id, executed_at 
FROM trades 
ORDER BY executed_at DESC 
LIMIT 10;
```

### Reset & Reseed Database:
```powershell
Get-Content .\scripts\seed_test.sql | docker exec -i trading-postgres psql -U trading_user -d trading_db
```

---

## 5. Direct gRPC Testing (grpcurl)

### Check C++ Engine (`localhost:50051`):
```powershell
# List exposed services
grpcurl -plaintext localhost:50051 list

# Submit order directly to C++ engine
grpcurl -plaintext -d '{
  "order": {
    "order_id": "CLI-TEST-1",
    "user_id": "00000000-0000-0000-0000-000000000001",
    "symbol": "AAPL",
    "side": "BUY",
    "type": "LIMIT",
    "quantity": 5,
    "price": 150.00
  }
}' localhost:50051 trading.ExecutionService/SubmitOrder
```

### Check Java Orchestrator (`localhost:50052`):
```powershell
grpcurl -plaintext -d '{"user_id": "00000000-0000-0000-0000-000000000001"}' localhost:50052 trading.OrchestratorService/GetPortfolio
```

---

## 6. Maintenance & Troubleshooting

### Freeing Port 8080 if Already in Use:
```powershell
$conn = Get-NetTCPConnection -LocalPort 8080 -ErrorAction SilentlyContinue
if ($conn) { Stop-Process -Id $conn.OwningProcess -Force }
```

### Stopping All Background Services:
```powershell
docker compose down
```

### Rebuilding C++ Engine After Code Changes:
```powershell
docker compose build cpp-engine
docker compose up -d cpp-engine
```

### Recompiling Java Orchestrator:
```powershell
cd orchestrator
.\gradlew compileJava
```

---

## 7. Python Strategy Engine (Phase 4)

### Compile Proto Stubs:
```powershell
cd strategy
python generate_proto.py
```

### Run Automated Strategy Unit Tests:
```powershell
cd strategy
python -m unittest discover tests
```

### Run Bot with Default Moving Average Crossover (Mock Data):
```powershell
cd strategy
python src/main.py
```

### Run Bot with RSI Strategy on Live Data (Yahoo Finance):
```powershell
cd strategy
$env:TRADING_SYMBOL = "AAPL"
$env:TRADING_STRATEGY = "RSI"
$env:USE_MOCK_DATA = "false"
python src/main.py
```

