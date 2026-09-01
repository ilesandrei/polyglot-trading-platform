package com.trading.service;

import com.trading.entity.Order;
import com.trading.entity.Portfolio;
import com.trading.entity.Position;
import com.trading.entity.Trade;
import com.trading.repository.PortfolioRepository;
import com.trading.repository.PositionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * PortfolioService — updates balances and positions after a confirmed trade.
 *
 * This is called AFTER the C++ engine confirms a trade via the StreamExecutions
 * gRPC stream. At that point, money and shares actually change hands.
 *
 * What happens on a trade:
 *
 *   BUYER side:
 *     - cash           -= trade.quantity * trade.price
 *     - position.qty   += trade.quantity
 *     - position.avg    = weighted average of old cost + new fill
 *
 *   SELLER side:
 *     - cash           += trade.quantity * trade.price
 *     - position.qty   -= trade.quantity
 *     - (average_cost stays the same — PnL is realized but not tracked here yet)
 *
 * Both updates must be atomic (annotated @Transactional).
 * If one fails, neither should be committed.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PortfolioService {

    private final PortfolioRepository portfolioRepository;
    private final PositionRepository  positionRepository;

    /**
     * Apply the effects of a trade to both the buyer's and seller's portfolios.
     *
     * @param trade      the executed trade (from C++ engine, already persisted)
     * @param buyerOrder the order on the buy side (used to resolve user_id)
     * @param sellerOrder the order on the sell side
     */
    @Transactional
    public void applyTrade(Trade trade, Order buyerOrder, Order sellerOrder) {
        log.info("[PORTFOLIO] Applying trade {} — {} {} @ {}",
            trade.getId(), trade.getSymbol(), trade.getQuantity(), trade.getPrice());

        // TODO: call applyBuy() and applySell() here.
        //       If either throws, the @Transactional annotation rolls back both.
        //
        //   applyBuy(trade, buyerOrder.getUserId());
        //   applySell(trade, sellerOrder.getUserId());

        throw new UnsupportedOperationException("TODO: implement applyTrade");
    }

    // ─────────────────────────────────────────────────────────────────────
    //  Buyer update
    // ─────────────────────────────────────────────────────────────────────

    private void applyBuy(Trade trade, UUID buyerId) {
        // TODO:
        //  1. Load the buyer's portfolio: portfolioRepository.findByUserId(buyerId)
        //  2. Deduct cash: portfolio.setCash( cash - quantity * price )
        //  3. Load (or create) the buyer's position for trade.getSymbol()
        //  4. Update position quantity: pos.qty += trade.quantity
        //  5. Recalculate average cost:
        //       new_avg = (old_qty * old_avg + trade.qty * trade.price) / new_qty
        //  6. Save portfolio and position (portfolioRepository.save / positionRepository.save)

        throw new UnsupportedOperationException("TODO: implement applyBuy");
    }

    // ─────────────────────────────────────────────────────────────────────
    //  Seller update
    // ─────────────────────────────────────────────────────────────────────

    private void applySell(Trade trade, UUID sellerId) {
        // TODO:
        //  1. Load the seller's portfolio
        //  2. Add cash: portfolio.setCash( cash + quantity * price )
        //  3. Load the seller's position for trade.getSymbol()
        //  4. Deduct quantity: pos.qty -= trade.quantity
        //     (if quantity reaches 0, you may delete the position row or leave it at 0)
        //  5. Save portfolio and position

        throw new UnsupportedOperationException("TODO: implement applySell");
    }
}
