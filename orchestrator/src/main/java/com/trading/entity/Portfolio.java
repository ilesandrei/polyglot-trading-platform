package com.trading.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * JPA entity mapped to the `portfolios` table (infra/db/init.sql).
 *
 * One portfolio per user. Holds:
 *  - cash  : available USD (starts at $100,000 for demo user)
 *  - positions : the assets the user currently holds (one-to-many → Position)
 */
@Entity
@Table(name = "portfolios")
@Getter @Setter @NoArgsConstructor
public class Portfolio {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    /**
     * Foreign key to `users.id`.
     * Unique constraint ensures one portfolio per user.
     */
    @Column(name = "user_id", nullable = false, unique = true)
    private UUID userId;

    /**
     * Available cash balance.
     * Decremented when a BUY order is submitted (reserved).
     * Incremented when a SELL trade executes.
     */
    @Column(nullable = false, precision = 18, scale = 8)
    private BigDecimal cash;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    // TODO: add a @OneToMany(mappedBy = "portfolio") List<Position> positions
    //       so that getPortfolio() can return the full holdings snapshot.
}
