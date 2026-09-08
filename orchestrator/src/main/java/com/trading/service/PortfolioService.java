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

        // If either throws, the @Transactional annotation rolls back both.
        applyBuy(trade, buyerOrder.getUserId());
        applySell(trade, sellerOrder.getUserId());
    }

    /**
     * Apply an instant paper market fill for a single user (acting against the market maker).
     */
    @Transactional
    public void applyPaperTrade(Trade trade, UUID userId, String side) {
        if ("BUY".equalsIgnoreCase(side)) {
            applyBuy(trade, userId);
        } else {
            applySell(trade, userId);
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    //  Buyer update
    // ─────────────────────────────────────────────────────────────────────

    private void applyBuy(Trade trade, UUID buyerId) {
        // 1. Load the buyer's portfolio
        Portfolio portfolio = portfolioRepository.findByUserId(buyerId)
            .orElseThrow(() -> new IllegalStateException("Portfolio not found for buyer: " + buyerId));

        // 2. Deduct cash = quantity * price
        BigDecimal totalCost = trade.getQuantity().multiply(trade.getPrice());
        portfolio.setCash(portfolio.getCash().subtract(totalCost));
        portfolio.setUpdatedAt(OffsetDateTime.now());
        
        // 3. Load or create the position for this symbol
        Position position = positionRepository
            .findByPortfolioIdAndSymbol(portfolio.getId(), trade.getSymbol())
            .orElseGet(() -> {
                Position newPos = new Position();
                newPos.setPortfolioId(portfolio.getId());
                newPos.setSymbol(trade.getSymbol());
                newPos.setQuantity(BigDecimal.ZERO);
                newPos.setAverageCost(BigDecimal.ZERO);
                return newPos;
            });
        
        // 4 & 5. Update quantity and recalculate weighted average cost
        BigDecimal oldQty = position.getQuantity();
        BigDecimal oldAvg = position.getAverageCost();
        BigDecimal fillQty = trade.getQuantity();
        BigDecimal fillPrice = trade.getPrice();
        BigDecimal newQty = oldQty.add(fillQty);
        
        if (newQty.compareTo(BigDecimal.ZERO) > 0) {
            // new_avg = (old_qty * old_avg + fill_qty * fill_price) / new_qty
            BigDecimal oldCostBasis = oldQty.multiply(oldAvg);
            BigDecimal fillCostBasis = fillQty.multiply(fillPrice);
            BigDecimal totalCostBasis = oldCostBasis.add(fillCostBasis);
            BigDecimal newAvg = totalCostBasis.divide(newQty, 8, java.math.RoundingMode.HALF_UP);
            position.setAverageCost(newAvg);
        }
        position.setQuantity(newQty);
        position.setUpdatedAt(OffsetDateTime.now());
        // 6. Save both entities
        portfolioRepository.save(portfolio);
        positionRepository.save(position);
        log.info("[PORTFOLIO] Buyer {} updated: cash={}, {} pos={} @ avg={}",
            buyerId, portfolio.getCash(), trade.getSymbol(), position.getQuantity(), position.getAverageCost());
    }

    // ─────────────────────────────────────────────────────────────────────
    //  Seller update
    // ─────────────────────────────────────────────────────────────────────

    private void applySell(Trade trade, UUID sellerId) {
        // 1. Load the seller's portfolio
        Portfolio portfolio = portfolioRepository.findByUserId(sellerId)
            .orElseThrow(() -> new IllegalStateException("Portfolio not found for seller: " + sellerId));
        
            // 2. Add cash proceeds = quantity * price
        BigDecimal proceeds = trade.getQuantity().multiply(trade.getPrice());
        portfolio.setCash(portfolio.getCash().add(proceeds));
        portfolio.setUpdatedAt(OffsetDateTime.now());
        
        // 3. Load the seller's position for this symbol
        Position position = positionRepository
            .findByPortfolioIdAndSymbol(portfolio.getId(), trade.getSymbol())
            .orElseThrow(() -> new IllegalStateException(
                "No position found for seller " + sellerId + " in " + trade.getSymbol()));
        
                // 4. Deduct quantity
        BigDecimal oldQty = position.getQuantity();
        BigDecimal sellQty = trade.getQuantity();
        if (oldQty.compareTo(sellQty) < 0) {
            throw new IllegalStateException(String.format(
                "Oversell detected for seller %s in %s: holding %s, selling %s",
                sellerId, trade.getSymbol(), oldQty, sellQty));
        }
        BigDecimal newQty = oldQty.subtract(sellQty);
        portfolioRepository.save(portfolio);

        // If fully sold out, remove position from active holdings
        if (newQty.compareTo(BigDecimal.ZERO) <= 0) {
            positionRepository.delete(position);
            log.info("[PORTFOLIO] Seller {} closed entire position in {}. Position removed.",
                sellerId, trade.getSymbol());
        } else {
            position.setQuantity(newQty);
            position.setUpdatedAt(OffsetDateTime.now());
            positionRepository.save(position);
            log.info("[PORTFOLIO] Seller {} updated: cash={}, {} remaining_pos={}",
                sellerId, portfolio.getCash(), trade.getSymbol(), position.getQuantity());
        }
    }
}
