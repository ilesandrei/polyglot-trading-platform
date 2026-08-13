#pragma once
#include "order.hpp"
#include <map>
#include <list>
#include <unordered_map>
#include <optional>
#include <functional>
#include <vector>

// ═══════════════════════════════════════════════════════════════
//  order_book.hpp — Limit Order Book (LOB)
//
//  STEP 2: Implement after order.hpp is done.
//
//  The OrderBook keeps two sorted lists of orders:
//    - Bids (BUY orders):  sorted by price DESCENDING (highest bid first)
//    - Asks (SELL orders): sorted by price ASCENDING  (lowest ask first)
//
//  Within each price level, orders are sorted by time (FIFO).
//  This is called "price-time priority".
//
//  Data structure layout:
//
//    bids_: std::map<double, std::list<Order>, std::greater<double>>
//              │                    │
//              └── price level      └── FIFO queue at that price
//
//    asks_: std::map<double, std::list<Order>>   (ascending = default)
//
//    order_index_: unordered_map<order_id, iterator>
//              └── for O(1) cancel lookup
// ═══════════════════════════════════════════════════════════════

namespace engine {

// A price level is a queue of orders at the same price
using PriceLevel = std::list<Order>;

// Bids: highest price first
using BidLevels  = std::map<double, PriceLevel, std::greater<double>>;

// Asks: lowest price first
using AskLevels  = std::map<double, PriceLevel>;

class OrderBook {
public:
    explicit OrderBook(const std::string& symbol);

    // TODO STEP 2a: Implement add_order(Order order)
    // - If side == BUY  → insert into bids_ at price level order.price
    // - If side == SELL → insert into asks_ at price level order.price
    // - Also store an iterator in order_index_ for fast cancel access
    void add_order(Order order);

    // TODO STEP 2b: Implement cancel_order(const std::string& order_id)
    // - Look up the order in order_index_
    // - Erase it from the correct bid/ask price level list
    // - If the price level is now empty, erase the price level too
    // - Remove from order_index_
    // Returns true if cancelled, false if not found
    bool cancel_order(const std::string& order_id);

    // TODO STEP 2c: Implement best_bid() and best_ask()
    // - Return the highest bid price and lowest ask price
    // - Return std::nullopt if the book side is empty
    std::optional<double> best_bid() const;
    std::optional<double> best_ask() const;

    // Getters for the matching engine to iterate
    BidLevels& bids() { return bids_; }
    AskLevels& asks() { return asks_; }

    const std::string& symbol() const { return symbol_; }

private:
    std::string symbol_;
    BidLevels   bids_;
    AskLevels   asks_;

    // Maps order_id → iterator into the PriceLevel list
    // Needed for O(1) cancel without scanning the whole book
    struct OrderLocation {
        bool   is_bid;
        double price;
        PriceLevel::iterator it;
    };
    std::unordered_map<std::string, OrderLocation> order_index_;
};

} // namespace engine
