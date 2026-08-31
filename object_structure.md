# C++ Engine — Object Structure

## Core Types

### `Order` — [`order.hpp`](file:///c:/Users/andii/Desktop/p_p_t/engine/src/order.hpp)
The fundamental unit. Represents one buy/sell request.

```cpp
struct Order {
    std::string order_id;    // UUID from Java orchestrator
    std::string user_id;
    std::string symbol;      // e.g. "AAPL"
    Side        side;        // BUY | SELL
    Type        type;        // LIMIT | MARKET
    double      quantity;    // total amount requested
    double      filled_qty;  // how much matched so far (starts at 0)
    double      price;       // limit price (ignored for MARKET)
    int64_t     timestamp;   // unix ms — used for time-priority (FIFO)
    Status      status;      // PENDING | PARTIALLY_FILLED | FILLED | CANCELLED | REJECTED

    double remaining_qty()  // quantity - filled_qty
    bool   is_complete()    // filled_qty >= quantity
}
```

---

### `Trade` — [`matching_engine.hpp`](file:///c:/Users/andii/Desktop/p_p_t/engine/src/matching_engine.hpp)
The output of a match. Created by the engine whenever two orders cross.

```cpp
struct Trade {
    std::string trade_id;
    std::string buy_order_id;
    std::string sell_order_id;
    std::string symbol;
    double      quantity;      // how much was traded in this match
    double      price;         // price at which match occurred
    int64_t     timestamp_ms;
}
```

> [!NOTE]
> One `Order` can produce **multiple** `Trade` objects if it fills across several resting orders at different price levels (partial fills).

---

## `OrderBook` — [`order_book.hpp`](file:///c:/Users/andii/Desktop/p_p_t/engine/src/order_book.hpp)
Holds all resting orders for **one symbol**. Two sorted sides.

```
OrderBook { symbol: "AAPL" }
│
├── bids_: BidLevels   →  map<double, PriceLevel, greater<double>>
│   │                     ^^^^^ sorted DESCENDING (best bid first)
│   ├── [102.00] → PriceLevel (list<Order>)  ← front = oldest (FIFO)
│   │              [ order_A, order_B ]
│   └── [101.50] → PriceLevel
│                  [ order_C ]
│
└── asks_: AskLevels   →  map<double, PriceLevel>
    │                     ^^^^^ sorted ASCENDING (best ask first)
    ├── [103.00] → PriceLevel
    │              [ order_D ]
    └── [104.00] → PriceLevel
                   [ order_E, order_F ]
```

#### Internal index for fast cancel
```cpp
// Allows O(1) cancel without scanning the whole book
struct OrderLocation {
    bool                is_bid;   // which side?
    double              price;    // which price level?
    PriceLevel::iterator it;      // direct iterator into the list
};

unordered_map<string, OrderLocation>  order_index_;
//            ^order_id
```

#### Type aliases
```cpp
using PriceLevel = std::list<Order>;                              // FIFO queue at one price
using BidLevels  = std::map<double, PriceLevel, greater<double>>; // descending
using AskLevels  = std::map<double, PriceLevel>;                  // ascending (default)
```

---

## `MatchingEngine` — [`matching_engine.hpp`](file:///c:/Users/andii/Desktop/p_p_t/engine/src/matching_engine.hpp)
Top-level engine. Owns **one `OrderBook` per symbol**.

```
MatchingEngine
│
├── books_: unordered_map<string, OrderBook>
│   ├── "AAPL" → OrderBook { bids_, asks_, order_index_ }
│   ├── "BTC"  → OrderBook { ... }
│   └── ...
│
└── on_trade_: TradeCallback    // function called on every match
```

```cpp
using TradeCallback = std::function<void(const Trade&)>;
```

---

## Data Flow

```
process_order(Order)
    │
    ├─ 1. Find/create OrderBook for order.symbol
    │
    ├─ 2. match(order, book)
    │       │
    │       ├─ incoming BUY?  → iterate book.asks() low→high
    │       ├─ incoming SELL? → iterate book.bids() high→low
    │       │
    │       └─ for each resting order that can_match():
    │               fill = min(incoming.remaining_qty(), resting.remaining_qty())
    │               incoming.filled_qty += fill
    │               resting.filled_qty  += fill
    │               → emit Trade via on_trade_(trade)
    │               → if resting complete: remove from book
    │
    └─ 3. If LIMIT and still has remaining_qty → book.add_order(order)  [it rests]
          If MARKET and still has remaining_qty → status = CANCELLED
```

---

## Enum Summary

| Enum | Values |
|------|--------|
| `Side`   | `BUY`, `SELL` |
| `Type`   | `LIMIT`, `MARKET` |
| `Status` | `PENDING`, `PARTIALLY_FILLED`, `FILLED`, `CANCELLED`, `REJECTED` |
