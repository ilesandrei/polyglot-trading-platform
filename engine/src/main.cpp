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
    return 0;
}
