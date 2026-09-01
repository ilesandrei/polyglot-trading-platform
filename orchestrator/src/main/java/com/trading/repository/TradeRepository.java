package com.trading.repository;

import com.trading.entity.Trade;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Spring Data repository for the `trades` table.
 */
@Repository
public interface TradeRepository extends JpaRepository<Trade, UUID> {

    /**
     * Find all trades where the user was the buyer.
     * Used by: portfolio update logic, trade history REST endpoint.
     *
     * SQL: SELECT * FROM trades WHERE buy_order_id IN
     *        (SELECT id FROM orders WHERE user_id = ?)
     *
     * TODO: you may want a @Query with a JOIN to make this a single SQL call.
     */
    List<Trade> findByBuyOrderId(UUID buyOrderId);

    /**
     * Find all trades where the user was the seller.
     */
    List<Trade> findBySellOrderId(UUID sellOrderId);

    /**
     * All trades for a given symbol, newest first.
     * Used by: WebSocket live trade feed.
     */
    List<Trade> findBySymbolOrderByExecutedAtDesc(String symbol);
}
