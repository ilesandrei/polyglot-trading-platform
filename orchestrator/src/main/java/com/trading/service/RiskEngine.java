package com.trading.service;

import com.trading.entity.Order;
import com.trading.entity.Portfolio;
import com.trading.entity.Position;
import com.trading.repository.PortfolioRepository;
import com.trading.repository.PositionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

/**
 * RiskEngine — validates incoming orders against the user's current portfolio.
 *
 * This is the financial gatekeeper. An order that passes here gets forwarded
 * to the C++ matching engine. An order that fails is rejected immediately
 * without ever touching the engine.
 *
 * Rules to implement:
 *
 *   BUY  orders: check that the user has enough CASH to cover the order.
 *                required_cash = quantity * price  (for LIMIT orders)
 *                For MARKET orders you may need to estimate cost or use a
 *                configurable max-spend limit.
 *
 *   SELL orders: check that the user actually HOLDS enough of the symbol.
 *                required_qty = order.quantity
 *                available_qty = position.quantity  (from `positions` table)
 *
 * After validation, the risk engine does NOT update balances yet —
 * balances are updated only after a Trade confirms execution.
 * (Optional advanced feature: "reserve" cash at validation time to prevent
 *  over-spending on concurrent orders. Start without this for simplicity.)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RiskEngine {

    private final PortfolioRepository portfolioRepository;
    private final PositionRepository  positionRepository;

    /**
     * Validates the order against the user's current portfolio.
     *
     * @param order the incoming order (not yet persisted)
     * @return RiskResult.APPROVED or RiskResult.REJECTED with a reason message
     */
    public RiskResult validate(Order order) {
        UUID userId = order.getUserId();

        // ── 1. Load portfolio ─────────────────────────────────────────────
        Optional<Portfolio> portfolioOpt = portfolioRepository.findByUserId(userId);
        if (portfolioOpt.isEmpty()) {
            return RiskResult.reject("No portfolio found for user: " + userId);
        }
        Portfolio portfolio = portfolioOpt.get();

        // ── 2. Branch on order side ───────────────────────────────────────
        return switch (order.getSide()) {
            case "BUY"  -> validateBuy(order, portfolio);
            case "SELL" -> validateSell(order, portfolio);
            default     -> RiskResult.reject("Unknown order side: " + order.getSide());
        };
    }

    // ─────────────────────────────────────────────────────────────────────
    //  BUY validation
    // ─────────────────────────────────────────────────────────────────────

    private RiskResult validateBuy(Order order, Portfolio portfolio) {
        // TODO: Calculate the cash required for this order.
        //       For a LIMIT order: required = order.getQuantity() * order.getPrice()
        //       For a MARKET order: you need a strategy (e.g., reject, use last price, etc.)
        //
        //       Then compare against portfolio.getCash().
        //       If portfolio.getCash().compareTo(required) < 0 → reject
        //
        // Example structure:
        //   BigDecimal required = order.getQuantity().multiply(order.getPrice());
        //   if (portfolio.getCash().compareTo(required) < 0) {
        //       return RiskResult.reject("Insufficient cash. Have: " + portfolio.getCash()
        //                                + " Need: " + required);
        //   }
        //   return RiskResult.approve();

        throw new UnsupportedOperationException("TODO: implement BUY risk check");
    }

    // ─────────────────────────────────────────────────────────────────────
    //  SELL validation
    // ─────────────────────────────────────────────────────────────────────

    private RiskResult validateSell(Order order, Portfolio portfolio) {
        // TODO: Look up the user's position for this symbol.
        //       Use positionRepository.findByPortfolioIdAndSymbol(portfolio.getId(), order.getSymbol())
        //
        //       If no position exists, or position.getQuantity() < order.getQuantity() → reject.
        //
        // Example structure:
        //   Optional<Position> posOpt = positionRepository
        //       .findByPortfolioIdAndSymbol(portfolio.getId(), order.getSymbol());
        //   if (posOpt.isEmpty()) {
        //       return RiskResult.reject("No position in " + order.getSymbol());
        //   }
        //   Position pos = posOpt.get();
        //   if (pos.getQuantity().compareTo(order.getQuantity()) < 0) {
        //       return RiskResult.reject("Insufficient holdings. Have: " + pos.getQuantity()
        //                                + " Selling: " + order.getQuantity());
        //   }
        //   return RiskResult.approve();

        throw new UnsupportedOperationException("TODO: implement SELL risk check");
    }

    // ─────────────────────────────────────────────────────────────────────
    //  Result type
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Simple value object returned by validate().
     * approved == true → forward to engine.
     * approved == false → respond REJECTED immediately.
     */
    public record RiskResult(boolean approved, String reason) {
        static RiskResult approve()            { return new RiskResult(true,  "OK"); }
        static RiskResult reject(String reason){ return new RiskResult(false, reason); }
    }
}
