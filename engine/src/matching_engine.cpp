#include "matching_engine.hpp"
#include <chrono>
#include <algorithm>
#include <stdexcept>

// ═══════════════════════════════════════════════════════════════
//  matching_engine.cpp — Implement the matching logic here
// ═══════════════════════════════════════════════════════════════

namespace engine {

MatchingEngine::MatchingEngine(TradeCallback on_trade)
    : on_trade_(std::move(on_trade)) {}

// Helper to generate a trade ID (simple timestamp-based for now)
static std::string make_trade_id() {
    auto now = std::chrono::system_clock::now().time_since_epoch();
    return "T-" + std::to_string(
        std::chrono::duration_cast<std::chrono::nanoseconds>(now).count()
    );
}

static int64_t now_ms() {
    return std::chrono::duration_cast<std::chrono::milliseconds>(
        std::chrono::system_clock::now().time_since_epoch()
    ).count();
}

// ─── process_order ────────────────────────────────────────────
Order MatchingEngine::process_order(Order order) {
    // TODO STEP 3a:
    // 1. Find or create the book: books_.try_emplace(order.symbol, order.symbol)
    // 2. Get a reference to the book
    // 3. Call match(order, book)
    // 4. If order is LIMIT and NOT fully filled → book.add_order(order)
    // 5. Return the order
    
    auto [iterator, inserted] = books_.try_emplace(order.symbol, order.symbol);
    OrderBook& book = iterator->second;
    
    match(order, book);

    if(order.remaining_qty() > 0 && order.type == Type::LIMIT){
        order.status = Status::PENDING;
        book.add_order(order;)
    } else if (order.remaining_qty() > 0 && order.type == Type::MARKET) {
        order.status = Status::CANCELLED;
    }
    return order;
}

// ─── cancel_order ─────────────────────────────────────────────
bool MatchingEngine::cancel_order(const std::string& symbol,
                                   const std::string& order_id) {
    // TODO STEP 3b:
    // 1. Find the book in books_. If not found, return false.
    // 2. Call book.cancel_order(order_id) and return the result
    return false;
}

// ─── can_match ────────────────────────────────────────────────
bool MatchingEngine::can_match(const Order& incoming,
                                double resting_price) const {
    // TODO: A MARKET order always matches (return true)
    // A LIMIT BUY  matches if incoming.price >= resting_price
    // A LIMIT SELL matches if incoming.price <= resting_price
    return false;
}

// ─── match ────────────────────────────────────────────────────
void MatchingEngine::match(Order& incoming, OrderBook& book) {
    // TODO STEP 3c:
    // See the header file for the full algorithm description.
    //
    // Hints:
    //   - For a BUY  incoming, iterate book.asks() (lowest first)
    //   - For a SELL incoming, iterate book.bids() (highest first)
    //   - Use a while loop: while remaining_qty > 0 && !levels.empty()
    //   - For each level, check can_match(incoming, level_price)
    //   - For each resting order at front of the FIFO queue:
    //       double fill = std::min(incoming.remaining_qty(),
    //                              resting.remaining_qty());
    //       incoming.filled_qty += fill;
    //       resting.filled_qty  += fill;
    //   - Build a Trade and call on_trade_(trade)
    //   - If resting is complete: erase from list, if list empty erase level
    //   - Update incoming.status at the end
}

} // namespace engine
