#pragma once
#include "order.hpp"
#include "order_book.hpp"
#include <string>
#include <functional>
#include <unordered_map>
#include <vector>

// ═══════════════════════════════════════════════════════════════
//  matching_engine.hpp — Price-Time Priority Matching
//
//  STEP 3: Implement after order_book is done.
//
//  The MatchingEngine holds one OrderBook per symbol.
//  When a new order arrives, it tries to match it against
//  resting orders on the opposite side of the book.
//
//  Matching Rules:
//    - A BUY  order matches against the LOWEST available ask
//    - A SELL order matches against the HIGHEST available bid
//    - A match only happens if:
//        BUY  order price >= best ask price  (buyer willing to pay)
//        SELL order price <= best bid price  (seller willing to accept)
//    - For MARKET orders, always match regardless of price
//    - Each match generates a Trade object
//    - Partial fills are allowed: one order may match across
//      multiple resting orders at different price levels
// ═══════════════════════════════════════════════════════════════

namespace engine {

// A Trade is the result of two orders being matched
struct Trade {
    std::string trade_id;
    std::string buy_order_id;
    std::string sell_order_id;
    std::string symbol;
    double      quantity;
    double      price;
    int64_t     timestamp_ms;
};

// Callback type: called every time a trade is generated
using TradeCallback = std::function<void(const Trade&)>;

class MatchingEngine {
public:
    explicit MatchingEngine(TradeCallback on_trade);

    // TODO STEP 3a: Implement process_order(Order order)
    // This is the main entry point. It should:
    //   1. Get or create the OrderBook for order.symbol
    //   2. Call match(order, book) to try to fill the order
    //   3. If the order is not fully filled and it's a LIMIT order,
    //      add the remainder to the book via book.add_order()
    //   4. Return the final order (with updated status and filled_qty)
    Order process_order(Order order);

    // TODO STEP 3b: Implement cancel_order(symbol, order_id)
    // Find the book for symbol and call book.cancel_order(order_id)
    bool cancel_order(const std::string& symbol, const std::string& order_id);

private:
    // TODO STEP 3c: Implement match(Order& incoming, OrderBook& book)
    // This is the core matching loop. It should:
    //   1. Determine the opposing side (if BUY, look at asks; if SELL, look at bids)
    //   2. While the incoming order has remaining_qty() > 0 AND there is a
    //      matching price on the opposite side:
    //        a. Take the front order from the best price level (FIFO)
    //        b. Calculate fill quantity = min(incoming.remaining_qty(),
    //                                        resting.remaining_qty())
    //        c. Update filled_qty on both incoming and resting orders
    //        d. Create a Trade and call on_trade_(trade)
    //        e. If resting order is complete, remove it from the book
    //        f. If not complete, update it in place and break (still resting)
    //   3. Update the incoming order's status:
    //        FILLED if fully matched
    //        PARTIALLY_FILLED if some but not all was matched
    //        PENDING if nothing was matched (will rest in book)
    void match(Order& incoming, OrderBook& book);

    // Helper: does this incoming order cross the book?
    bool can_match(const Order& incoming, double resting_price) const;

    TradeCallback on_trade_;
    std::unordered_map<std::string, OrderBook> books_;
};

} // namespace engine
