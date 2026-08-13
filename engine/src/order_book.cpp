#include "order_book.hpp"
#include <stdexcept>

// ═══════════════════════════════════════════════════════════════
//  order_book.cpp — Implement the OrderBook methods here
// ═══════════════════════════════════════════════════════════════

namespace engine {

OrderBook::OrderBook(const std::string& symbol) : symbol_(symbol) {}

// ─── add_order ────────────────────────────────────────────────
void OrderBook::add_order(Order order) {
    // TODO STEP 2a:
    // 1. Push the order to the back of the correct price level list
    //    (bids_[order.price] for BUY, asks_[order.price] for SELL)
    // 2. Get an iterator to the just-inserted element (use std::prev(list.end()))
    // 3. Store { is_bid, price, iterator } in order_index_[order.order_id]
    const std::string id = order.order_id;
    const double price = order.price;
    const bool is_bid = (order.side == Side::BUY);

    if(is_bid){
        bids_[price].push_back(order);
        auto iterator = std::prev(bids_[price].end());
        order_index_[id] = OrderLocation(is_bid, price, iterator);
    }
    
    else{
        asks_[price].push_back(order);
        auto iterator = std::prev(asks_[price].end());
        order_index_[id] = OrderLocation(is_bid, price, iterator);
    }

}

// ─── cancel_order ─────────────────────────────────────────────
bool OrderBook::cancel_order(const std::string& order_id) {
    // TODO STEP 2b:
    // 1. Find the order_id in order_index_. If not found, return false.
    // 2. Get the OrderLocation (is_bid, price, iterator)
    // 3. Find the right map (bids_ or asks_) and price level
    // 4. Erase the order from the list using the stored iterator
    // 5. If the price level list is now empty, erase the price level from the map
    // 6. Erase from order_index_
    // 7. Return true

    auto index_it = order_index_.find(order_id);
    if(index_it == order_index_.end()){
        return false;
    }

    // Extract the location data
    const OrderLocation& loc = index_it->seccond;
    const bool is_bid = loc.is_bid;
    const double price = loc.price;
    auto list_iterator = loc.it;

    if(is_bid){
        bids_[price].erase(list_iterator);
        
        if(bids_[price].empty())[
            bids_.erase(price);
        ]
    }
    else{
        asks_[price].erase(list_iterator);

        if(asks_[price].empty()){
            asks_.erase(price);
        }
    }

    order_index_.erase(index_it);

    return true;
}

// ─── best_bid ─────────────────────────────────────────────────
std::optional<double> OrderBook::best_bid() const {
    // TODO STEP 2c:
    // bids_ is sorted descending, so begin() is the highest price
    // Return std::nullopt if bids_ is empty
    
    if(bids_.empty()){ return std::nullopt; }

    return bids_.begin()->first;
}
    

// ─── best_ask ─────────────────────────────────────────────────
std::optional<double> OrderBook::best_ask() const {
    // TODO STEP 2c:
    // asks_ is sorted ascending, so begin() is the lowest price
    // Return std::nullopt if asks_ is empty
    return std::nullopt;

    if(asks_.empty()){ return std::nullopt; }

    return asks_.begin()->first;
}

} // namespace engine
