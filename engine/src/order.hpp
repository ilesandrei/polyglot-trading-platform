#pragma once
#include <string>
#include <cstdint>

// ═══════════════════════════════════════════════════════════════
//  order.hpp — Core Order data structure
//
//  An Order represents a single buy or sell request placed
//  by a user. It is the fundamental unit of the order book.
// ═══════════════════════════════════════════════════════════════

namespace engine {

enum class Side {
    BUY,
    SELL
};

enum class Type {
    LIMIT,
    MARKET
};

enum class Status {
    PENDING,
    PARTIALLY_FILLED,
    FILLED,
    CANCELLED,
    REJECTED
};

//   - order_id    : std::string  (UUID from the Java orchestrator)
//   - user_id     : std::string
//   - symbol      : std::string  (e.g. "AAPL")
//   - side        : Side         (BUY or SELL)
//   - type        : Type         (LIMIT or MARKET)
//   - quantity    : double       (total amount requested)
//   - filled_qty  : double       (how much has been matched so far, starts at 0)
//   - price       : double       (limit price; ignored for MARKET)
//   - timestamp   : int64_t      (unix ms, used for time-priority)
//   - status      : Status

struct Order {
    std::string order_id;
    std::string user_id;
    std::string symbol;
    Side side;
    Type type;
    double quantity;
    double filled_qty;
    double price;
    int64_t timestamp;
    Status status;

    double remaining_qty() const {
        return quantity - filled_qty;
    }

    bool is_complete() const {
        return filled_qty >= quantity;
    }
};

} // namespace engine
