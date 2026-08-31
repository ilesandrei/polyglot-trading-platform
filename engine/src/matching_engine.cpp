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
        book.add_order(order);
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

    //Look up the order book for this specific symbol (e.g., "AAPL")
    auto book_iterator = books_.find(symbol);
    if(book_iterator == books_.end()) { return false; }

    OrderBook& book = book_iterator->second;
    return book.cancel_order(order_id);
}

// ─── cancel_any ───────────────────────────────────────────────
bool MatchingEngine::cancel_any(const std::string& order_id) {
    for (auto& [symbol, book] : books_) {
        if (book.cancel_order(order_id)) { return true; }
    }
    return false;
}

// ─── can_match ────────────────────────────────────────────────
bool MatchingEngine::can_match(const Order& incoming,
                                double resting_price) const {
    // TODO: A MARKET order always matches (return true)
    // A LIMIT BUY  matches if incoming.price >= resting_price
    // A LIMIT SELL matches if incoming.price <= resting_price

    if( incoming.type == Type::MARKET ) { return true; }

    bool is_bid = ( incoming.side == Side::BUY );
    if(is_bid) {
        // A LIMIT BUY  matches if incoming.price >= resting_price
        return ( incoming.price >= resting_price );
    }
    else {
        // A LIMIT SELL matches if incoming.price <= resting_price
        return ( incoming.price <= resting_price );
    }
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

    bool is_bid = ( incoming.side == Side::BUY );

    while(incoming.remaining_qty() > 0) {

    // 1. Find the target side
    std::optional<double> best_price = is_bid ? book.best_ask() : book.best_bid();
    // 2. Check the price: If the book is empty, or the price is bad, STOP.
    if (!best_price.has_value() || !can_match(incoming, best_price.value()))
    {
            break;
    }

    //get the queue std::list for people sitting at this exact price
    PriceLevel& price_queue = is_bid ? book.asks()[best_price.value()]
                                     : book.bids()[best_price.value()];

    //Take the front order from the best price level (FIFO)
    Order& resting_order = price_queue.front();

    //Calculate the trade: The smaller of what I need vs what they have
    //Calculate fill quantity = min(incoming.remaining_qty(),
    //                                  resting.remaining_qty())
    double trade_qty = std::min(incoming.remaining_qty(), resting_order.remaining_qty());
    
    //update math
    incoming.filled_qty += trade_qty;
    resting_order.filled_qty += trade_qty;

    //transaction receit
    Trade trade;
    trade.symbol = incoming.symbol;
    trade.price = best_price.value();
    trade.quantity = trade_qty;
    trade.buy_order_id = is_bid ? incoming.order_id : resting_order.order_id;
    trade.sell_order_id = is_bid ? resting_order.order_id : incoming.order_id;
    
    on_trade_(trade);

    //Cleanup: If the resting order is empty, delete it!    
    if(resting_order.remaining_qty() == 0){
        book.cancel_order(resting_order.order_id);
    }
}

// 7. Final Status Update for the incoming order
    if (incoming.remaining_qty() == 0) {
        incoming.status = Status::FILLED;
    } else if (incoming.filled_qty > 0) {
        incoming.status = Status::PARTIALLY_FILLED;
    }

} // match()

} // namespace engine
