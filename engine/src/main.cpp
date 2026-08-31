#include "matching_engine.hpp"
#include <iostream>

// ═══════════════════════════════════════════════════════════════
//  main.cpp — Entry point (STEP 5, after everything else works)
//
//  For now this is a simple test harness so you can verify
//  your matching logic before wiring up gRPC.
// ═══════════════════════════════════════════════════════════════

using namespace engine;

int main() {
    // Create a matching engine and print every trade that occurs
    MatchingEngine engine([](const Trade& t) {
        std::cout << "[TRADE] "
                  << t.symbol    << " | "
                  << "qty: "     << t.quantity << " @ "
                  << "price: "   << t.price    << " | "
                  << "buy: "     << t.buy_order_id << " "
                  << "sell: "    << t.sell_order_id
                  << std::endl;
    });

    // TODO STEP 5: Once matching_engine is implemented, test with:
    //
    // 1. Add a resting SELL limit order at 100.0 for 10 shares of AAPL
    // 2. Add a BUY  limit order at 100.0 for 5 shares  → should match 5
    // 3. Add a BUY  market order for 3 shares           → should match 3
    // 4. Add a BUY  limit order at 99.0 for 5 shares   → should rest (no match)
    // 5. Cancel the resting BUY order
    //
    // Expected output: 2 trades printed

    std::cout << "Trading Engine started. Add test orders above." << std::endl;

    // 1. Add a resting SELL limit order at 100.0 for 10 shares of AAPL
    Order sell_1;
    sell_1.order_id = "ORDER_1";
    sell_1.symbol = "AAPL";
    sell_1.side = Side::SELL;
    sell_1.type = Type::LIMIT;
    sell_1.quantity = 10.0;
    sell_1.price = 100.0;
    sell_1.filled_qty = 0.0;
    engine.process_order(sell_1);
    std::cout << "Added SELL Limit: 10 @ 100.0\n";

    // 2. Add a BUY limit order at 100.0 for 5 shares -> should match 5
    Order buy_1;
    buy_1.order_id = "ORDER_2";
    buy_1.symbol = "AAPL";
    buy_1.side = Side::BUY;
    buy_1.type = Type::LIMIT;
    buy_1.quantity = 5.0;
    buy_1.price = 100.0;
    buy_1.filled_qty = 0.0;
    engine.process_order(buy_1);
    std::cout << "Added BUY Limit: 5 @ 100.0\n";

    // 3. Add a BUY market order for 3 shares -> should match 3
    Order buy_market;
    buy_market.order_id = "ORDER_3";
    buy_market.symbol = "AAPL";
    buy_market.side = Side::BUY;
    buy_market.type = Type::MARKET;
    buy_market.quantity = 3.0;
    buy_market.price = 0.0; // Market orders don't need a price
    buy_market.filled_qty = 0.0;
    engine.process_order(buy_market);
    std::cout << "Added BUY Market: 3\n";

    // 4. Add a BUY limit order at 99.0 for 5 shares -> should rest (no match)
    Order buy_rest;
    buy_rest.order_id = "ORDER_4";
    buy_rest.symbol = "AAPL";
    buy_rest.side = Side::BUY;
    buy_rest.type = Type::LIMIT;
    buy_rest.quantity = 5.0;
    buy_rest.price = 99.0;
    buy_rest.filled_qty = 0.0;
    engine.process_order(buy_rest);
    std::cout << "Added BUY Limit: 5 @ 99.0 (Should rest on book)\n";

    // 5. Cancel the resting BUY order
    bool cancelled = engine.cancel_order("AAPL", "ORDER_4");
    if (cancelled) {
        std::cout << "[INFO] Order_4 was successfully cancelled from the book!\n";
    } else {
        std::cout << "[ERROR] Failed to cancel Order_4!\n";
    }

    std::cout << "--- Tests Complete ---" << std::endl;
    return 0;
}
