// ═══════════════════════════════════════════════════════════════
//  test_matching_engine.cpp — Unit tests for MatchingEngine
//
//  Tests the full order processing pipeline: matching, partial
//  fills, market orders, price-time priority, and cancellation.
//  The TradeCallback is used to capture and assert on trades.
// ═══════════════════════════════════════════════════════════════

#include <gtest/gtest.h>
#include "../src/order.hpp"
#include "../src/matching_engine.hpp"

using namespace engine;

// ─── Test Fixture ─────────────────────────────────────────────────────────────
//
// Each test gets a fresh MatchingEngine and a vector that collects
// every Trade emitted during the test.

class MatchingEngineTest : public ::testing::Test {
protected:
    std::vector<Trade> fired_trades;

    MatchingEngine engine{[this](const Trade& t) {
        fired_trades.push_back(t);
    }};

    // ── Order factory helpers ──────────────────────────────────
    Order limit(const std::string& id, Side side, double price, double qty,
                const std::string& symbol = "AAPL") {
        Order o;
        o.order_id   = id;
        o.user_id    = "user-1";
        o.symbol     = symbol;
        o.side       = side;
        o.type       = Type::LIMIT;
        o.quantity   = qty;
        o.filled_qty = 0.0;
        o.price      = price;
        o.timestamp  = 0;
        o.status     = Status::PENDING;
        return o;
    }

    Order market(const std::string& id, Side side, double qty,
                 const std::string& symbol = "AAPL") {
        Order o;
        o.order_id   = id;
        o.user_id    = "user-1";
        o.symbol     = symbol;
        o.side       = side;
        o.type       = Type::MARKET;
        o.quantity   = qty;
        o.filled_qty = 0.0;
        o.price      = 0.0;
        o.timestamp  = 0;
        o.status     = Status::PENDING;
        return o;
    }
};

// ─── Basic matching ───────────────────────────────────────────────────────────

TEST_F(MatchingEngineTest, NoMatchWhenBookIsEmpty) {
    engine.process_order(limit("B1", Side::BUY, 100.0, 5.0));
    EXPECT_TRUE(fired_trades.empty());
}

TEST_F(MatchingEngineTest, LimitBuyMatchesRestingSell) {
    engine.process_order(limit("S1", Side::SELL, 100.0, 5.0));
    engine.process_order(limit("B1", Side::BUY,  100.0, 5.0));
    ASSERT_EQ(fired_trades.size(), 1u);
    EXPECT_DOUBLE_EQ(fired_trades[0].quantity, 5.0);
    EXPECT_DOUBLE_EQ(fired_trades[0].price,    100.0);
    EXPECT_EQ(fired_trades[0].buy_order_id,  "B1");
    EXPECT_EQ(fired_trades[0].sell_order_id, "S1");
}

TEST_F(MatchingEngineTest, LimitSellMatchesRestingBuy) {
    engine.process_order(limit("B1", Side::BUY,  100.0, 5.0));
    engine.process_order(limit("S1", Side::SELL, 100.0, 5.0));
    ASSERT_EQ(fired_trades.size(), 1u);
    EXPECT_EQ(fired_trades[0].buy_order_id,  "B1");
    EXPECT_EQ(fired_trades[0].sell_order_id, "S1");
}

TEST_F(MatchingEngineTest, LimitBuyDoesNotMatchIfPriceTooLow) {
    engine.process_order(limit("S1", Side::SELL, 105.0, 5.0));
    engine.process_order(limit("B1", Side::BUY,   99.0, 5.0));
    EXPECT_TRUE(fired_trades.empty());
}

TEST_F(MatchingEngineTest, LimitBuyMatchesWhenPriceExceedsAsk) {
    engine.process_order(limit("S1", Side::SELL, 100.0, 5.0));
    engine.process_order(limit("B1", Side::BUY,  101.0, 5.0));  // willing to pay more
    ASSERT_EQ(fired_trades.size(), 1u);
    EXPECT_DOUBLE_EQ(fired_trades[0].price, 100.0);  // executes at resting price
}

// ─── Market orders ────────────────────────────────────────────────────────────

TEST_F(MatchingEngineTest, MarketBuyMatchesAtAnyAskPrice) {
    engine.process_order(limit("S1", Side::SELL, 999.0, 5.0));
    Order result = engine.process_order(market("M1", Side::BUY, 5.0));
    ASSERT_EQ(fired_trades.size(), 1u);
    EXPECT_EQ(result.status, Status::FILLED);
}

TEST_F(MatchingEngineTest, MarketBuyCancelledIfBookEmpty) {
    Order result = engine.process_order(market("M1", Side::BUY, 5.0));
    EXPECT_EQ(result.status, Status::CANCELLED);
    EXPECT_TRUE(fired_trades.empty());
}

TEST_F(MatchingEngineTest, MarketSellCancelledIfBookEmpty) {
    Order result = engine.process_order(market("M1", Side::SELL, 5.0));
    EXPECT_EQ(result.status, Status::CANCELLED);
}

// ─── Order status after processing ───────────────────────────────────────────

TEST_F(MatchingEngineTest, FullyFilledOrderHasFilledStatus) {
    engine.process_order(limit("S1", Side::SELL, 100.0, 5.0));
    Order result = engine.process_order(limit("B1", Side::BUY, 100.0, 5.0));
    EXPECT_EQ(result.status, Status::FILLED);
    EXPECT_DOUBLE_EQ(result.filled_qty, 5.0);
}

TEST_F(MatchingEngineTest, PartiallyFilledOrderHasPartiallyFilledStatus) {
    engine.process_order(limit("S1", Side::SELL, 100.0, 3.0));  // only 3 available
    Order result = engine.process_order(limit("B1", Side::BUY, 100.0, 10.0)); // wants 10
    EXPECT_EQ(result.status, Status::PARTIALLY_FILLED);
    EXPECT_DOUBLE_EQ(result.filled_qty, 3.0);
    EXPECT_DOUBLE_EQ(result.remaining_qty(), 7.0);
}

TEST_F(MatchingEngineTest, UnfilledLimitOrderHasPendingStatus) {
    Order result = engine.process_order(limit("B1", Side::BUY, 99.0, 5.0));
    EXPECT_EQ(result.status, Status::PENDING);
    EXPECT_DOUBLE_EQ(result.filled_qty, 0.0);
}

// ─── Partial fills ────────────────────────────────────────────────────────────

TEST_F(MatchingEngineTest, PartialFillLeavesRestingOrderInBook) {
    engine.process_order(limit("S1", Side::SELL, 100.0, 10.0));
    engine.process_order(limit("B1", Side::BUY,  100.0,  4.0));  // only takes 4

    // Now submit another buy — should match the remaining 6
    Order result = engine.process_order(limit("B2", Side::BUY, 100.0, 6.0));
    EXPECT_EQ(result.status, Status::FILLED);
    ASSERT_EQ(fired_trades.size(), 2u);
}

TEST_F(MatchingEngineTest, IncomingOrderFiresOneTradePerRestingOrder) {
    // Two separate sell orders at same price
    engine.process_order(limit("S1", Side::SELL, 100.0, 5.0));
    engine.process_order(limit("S2", Side::SELL, 100.0, 5.0));

    // One big buy sweeps both
    engine.process_order(limit("B1", Side::BUY, 100.0, 10.0));
    EXPECT_EQ(fired_trades.size(), 2u);
}

// ─── Multi-level sweep ────────────────────────────────────────────────────────

TEST_F(MatchingEngineTest, BuySweepsMultiplePriceLevels) {
    engine.process_order(limit("S1", Side::SELL, 100.0, 5.0));
    engine.process_order(limit("S2", Side::SELL, 101.0, 5.0));

    Order result = engine.process_order(limit("B1", Side::BUY, 102.0, 10.0));
    EXPECT_EQ(result.status, Status::FILLED);
    ASSERT_EQ(fired_trades.size(), 2u);
    // First trade at lower ask (100), second at next level (101)
    EXPECT_DOUBLE_EQ(fired_trades[0].price, 100.0);
    EXPECT_DOUBLE_EQ(fired_trades[1].price, 101.0);
}

TEST_F(MatchingEngineTest, SellSweepsMultiplePriceLevels) {
    engine.process_order(limit("B1", Side::BUY, 102.0, 5.0));
    engine.process_order(limit("B2", Side::BUY, 101.0, 5.0));

    Order result = engine.process_order(limit("S1", Side::SELL, 100.0, 10.0));
    EXPECT_EQ(result.status, Status::FILLED);
    ASSERT_EQ(fired_trades.size(), 2u);
    // First trade at highest bid (102), second at next (101)
    EXPECT_DOUBLE_EQ(fired_trades[0].price, 102.0);
    EXPECT_DOUBLE_EQ(fired_trades[1].price, 101.0);
}

TEST_F(MatchingEngineTest, BuyStopsAtUncrossableLevel) {
    engine.process_order(limit("S1", Side::SELL, 100.0, 5.0));
    engine.process_order(limit("S2", Side::SELL, 102.0, 5.0));  // BUY won't reach this

    Order result = engine.process_order(limit("B1", Side::BUY, 101.0, 10.0));
    // Only fills against S1@100, S2@102 is beyond limit
    ASSERT_EQ(fired_trades.size(), 1u);
    EXPECT_EQ(result.status, Status::PARTIALLY_FILLED);
}

// ─── Price-time priority ──────────────────────────────────────────────────────

TEST_F(MatchingEngineTest, OlderOrderAtSamePriceFilledFirst) {
    engine.process_order(limit("FIRST",  Side::SELL, 100.0, 5.0));
    engine.process_order(limit("SECOND", Side::SELL, 100.0, 5.0));

    engine.process_order(limit("B1", Side::BUY, 100.0, 5.0));

    ASSERT_EQ(fired_trades.size(), 1u);
    EXPECT_EQ(fired_trades[0].sell_order_id, "FIRST");  // FIFO
}

// ─── Cancel ───────────────────────────────────────────────────────────────────

TEST_F(MatchingEngineTest, CancelRestingOrderSucceeds) {
    engine.process_order(limit("B1", Side::BUY, 99.0, 5.0));
    EXPECT_TRUE(engine.cancel_order("AAPL", "B1"));
}

TEST_F(MatchingEngineTest, CancelledOrderDoesNotMatch) {
    engine.process_order(limit("B1", Side::BUY, 100.0, 5.0));
    engine.cancel_order("AAPL", "B1");

    engine.process_order(limit("S1", Side::SELL, 100.0, 5.0));
    EXPECT_TRUE(fired_trades.empty());
}

TEST_F(MatchingEngineTest, CancelNonExistentOrderReturnsFalse) {
    EXPECT_FALSE(engine.cancel_order("AAPL", "GHOST"));
}

TEST_F(MatchingEngineTest, CancelAnyFindsOrderAcrossSymbols) {
    engine.process_order(limit("B1", Side::BUY, 100.0, 5.0, "AAPL"));
    engine.process_order(limit("B2", Side::BUY, 200.0, 2.0, "BTC"));
    EXPECT_TRUE(engine.cancel_any("B2"));
}

// ─── Multi-symbol isolation ───────────────────────────────────────────────────

TEST_F(MatchingEngineTest, OrdersForDifferentSymbolsDoNotInteract) {
    engine.process_order(limit("S1", Side::SELL, 100.0, 5.0, "AAPL"));
    engine.process_order(limit("B1", Side::BUY,  100.0, 5.0, "GOOGL"));  // different symbol
    EXPECT_TRUE(fired_trades.empty());
}

TEST_F(MatchingEngineTest, MatchingWorksIndependentlyPerSymbol) {
    engine.process_order(limit("S1", Side::SELL, 100.0, 5.0, "AAPL"));
    engine.process_order(limit("S2", Side::SELL, 200.0, 3.0, "BTC"));
    engine.process_order(limit("B1", Side::BUY,  100.0, 5.0, "AAPL"));
    engine.process_order(limit("B2", Side::BUY,  200.0, 3.0, "BTC"));

    ASSERT_EQ(fired_trades.size(), 2u);
    EXPECT_EQ(fired_trades[0].symbol, "AAPL");
    EXPECT_EQ(fired_trades[1].symbol, "BTC");
}
