// ═══════════════════════════════════════════════════════════════
//  test_order_book.cpp — Unit tests for the OrderBook class
//
//  Tests the data structure in isolation: adding orders, querying
//  best bid/ask, cancelling, and FIFO ordering within price levels.
// ═══════════════════════════════════════════════════════════════

#include <gtest/gtest.h>
#include "../src/order.hpp"
#include "../src/order_book.hpp"

using namespace engine;

// ─── Helpers ──────────────────────────────────────────────────────────────────

static Order make_order(const std::string& id, Side side, double price, double qty = 10.0) {
    Order o;
    o.order_id   = id;
    o.user_id    = "user-1";
    o.symbol     = "AAPL";
    o.side       = side;
    o.type       = Type::LIMIT;
    o.quantity   = qty;
    o.filled_qty = 0.0;
    o.price      = price;
    o.timestamp  = 0;
    o.status     = Status::PENDING;
    return o;
}

// ─── best_bid / best_ask ──────────────────────────────────────────────────────

TEST(OrderBook, EmptyBookHasNoBestBid) {
    OrderBook book("AAPL");
    EXPECT_FALSE(book.best_bid().has_value());
}

TEST(OrderBook, EmptyBookHasNoBestAsk) {
    OrderBook book("AAPL");
    EXPECT_FALSE(book.best_ask().has_value());
}

TEST(OrderBook, BestBidIsHighestPrice) {
    OrderBook book("AAPL");
    book.add_order(make_order("B1", Side::BUY, 100.0));
    book.add_order(make_order("B2", Side::BUY, 102.0));
    book.add_order(make_order("B3", Side::BUY, 101.0));
    ASSERT_TRUE(book.best_bid().has_value());
    EXPECT_DOUBLE_EQ(book.best_bid().value(), 102.0);
}

TEST(OrderBook, BestAskIsLowestPrice) {
    OrderBook book("AAPL");
    book.add_order(make_order("A1", Side::SELL, 105.0));
    book.add_order(make_order("A2", Side::SELL, 103.0));
    book.add_order(make_order("A3", Side::SELL, 104.0));
    ASSERT_TRUE(book.best_ask().has_value());
    EXPECT_DOUBLE_EQ(book.best_ask().value(), 103.0);
}

// ─── add_order ────────────────────────────────────────────────────────────────

TEST(OrderBook, AddBidAppearsOnBidSide) {
    OrderBook book("AAPL");
    book.add_order(make_order("B1", Side::BUY, 100.0));
    EXPECT_TRUE(book.best_bid().has_value());
    EXPECT_FALSE(book.best_ask().has_value());
}

TEST(OrderBook, AddAskAppearsOnAskSide) {
    OrderBook book("AAPL");
    book.add_order(make_order("A1", Side::SELL, 105.0));
    EXPECT_TRUE(book.best_ask().has_value());
    EXPECT_FALSE(book.best_bid().has_value());
}

TEST(OrderBook, MultipleBidsAtDifferentLevels) {
    OrderBook book("AAPL");
    book.add_order(make_order("B1", Side::BUY, 100.0));
    book.add_order(make_order("B2", Side::BUY, 99.0));
    EXPECT_EQ(book.bids().size(), 2u);
}

TEST(OrderBook, MultipleBidsAtSameLevelSharePriceQueue) {
    OrderBook book("AAPL");
    book.add_order(make_order("B1", Side::BUY, 100.0));
    book.add_order(make_order("B2", Side::BUY, 100.0));
    EXPECT_EQ(book.bids().size(), 1u);              // one price level
    EXPECT_EQ(book.bids().at(100.0).size(), 2u);   // two orders in it
}

// ─── cancel_order ─────────────────────────────────────────────────────────────

TEST(OrderBook, CancelExistingBidReturnsTrue) {
    OrderBook book("AAPL");
    book.add_order(make_order("B1", Side::BUY, 100.0));
    EXPECT_TRUE(book.cancel_order("B1"));
}

TEST(OrderBook, CancelExistingAskReturnsTrue) {
    OrderBook book("AAPL");
    book.add_order(make_order("A1", Side::SELL, 105.0));
    EXPECT_TRUE(book.cancel_order("A1"));
}

TEST(OrderBook, CancelNonExistentReturnsFalse) {
    OrderBook book("AAPL");
    EXPECT_FALSE(book.cancel_order("GHOST"));
}

TEST(OrderBook, CancelRemovesOrderFromBids) {
    OrderBook book("AAPL");
    book.add_order(make_order("B1", Side::BUY, 100.0));
    book.cancel_order("B1");
    EXPECT_FALSE(book.best_bid().has_value());
}

TEST(OrderBook, CancelRemovesOrderFromAsks) {
    OrderBook book("AAPL");
    book.add_order(make_order("A1", Side::SELL, 105.0));
    book.cancel_order("A1");
    EXPECT_FALSE(book.best_ask().has_value());
}

TEST(OrderBook, CancelLastOrderEmptiesPriceLevel) {
    OrderBook book("AAPL");
    book.add_order(make_order("B1", Side::BUY, 100.0));
    book.cancel_order("B1");
    EXPECT_EQ(book.bids().size(), 0u);   // price level map entry is gone
}

TEST(OrderBook, CancelOneOfTwoLeavesOneBehind) {
    OrderBook book("AAPL");
    book.add_order(make_order("B1", Side::BUY, 100.0));
    book.add_order(make_order("B2", Side::BUY, 100.0));
    book.cancel_order("B1");
    EXPECT_EQ(book.bids().at(100.0).size(), 1u);
    EXPECT_EQ(book.bids().at(100.0).front().order_id, "B2");
}

TEST(OrderBook, CancelDoesNotAffectOtherPriceLevels) {
    OrderBook book("AAPL");
    book.add_order(make_order("B1", Side::BUY, 100.0));
    book.add_order(make_order("B2", Side::BUY, 99.0));
    book.cancel_order("B1");
    EXPECT_TRUE(book.best_bid().has_value());
    EXPECT_DOUBLE_EQ(book.best_bid().value(), 99.0);
}

// ─── FIFO ordering ────────────────────────────────────────────────────────────

TEST(OrderBook, FIFOOrderWithinPriceLevel) {
    OrderBook book("AAPL");
    book.add_order(make_order("FIRST",  Side::BUY, 100.0));
    book.add_order(make_order("SECOND", Side::BUY, 100.0));
    book.add_order(make_order("THIRD",  Side::BUY, 100.0));

    auto& queue = book.bids().at(100.0);
    auto it = queue.begin();
    EXPECT_EQ((it++)->order_id, "FIRST");
    EXPECT_EQ((it++)->order_id, "SECOND");
    EXPECT_EQ(it->order_id,     "THIRD");
}
