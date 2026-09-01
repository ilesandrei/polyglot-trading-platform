package com.trading.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * JPA entity mapped to the `positions` table (infra/db/init.sql).
 *
 * Represents how many units of a symbol a user holds and at what average cost.
 * Updated after every trade that executes against this user's orders.
 *
 * Columns:
 *   portfolio_id  FK → portfolios.id
 *   symbol        e.g. "AAPL", "BTC-USD"
 *   quantity      current holdings (can be 0 when fully sold)
 *   average_cost  weighted average entry price
 */
@Entity
@Table(
    name = "positions",
    uniqueConstraints = @UniqueConstraint(columnNames = {"portfolio_id", "symbol"})
)
@Getter @Setter @NoArgsConstructor
public class Position {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    /**
     * Owning portfolio — many positions belong to one portfolio.
     */
    // TODO: add @ManyToOne @JoinColumn(name = "portfolio_id") Portfolio portfolio;

    @Column(name = "portfolio_id", nullable = false)
    private UUID portfolioId;

    @Column(nullable = false, length = 20)
    private String symbol;

    /**
     * How many units the user currently holds.
     * Goes UP when a BUY trade executes, DOWN when a SELL trade executes.
     */
    @Column(nullable = false, precision = 18, scale = 8)
    private BigDecimal quantity;

    /**
     * Weighted average purchase price.
     * Recalculated on every BUY fill:
     *   new_avg = (old_qty * old_avg + fill_qty * fill_price) / new_qty
     *
     * Not updated on SELL (cost basis stays the same until position closes).
     */
    @Column(name = "average_cost", nullable = false, precision = 18, scale = 8)
    private BigDecimal averageCost;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
