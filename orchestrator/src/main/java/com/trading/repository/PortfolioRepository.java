package com.trading.repository;

import com.trading.entity.Portfolio;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data repository for the `portfolios` table.
 * Spring auto-generates all CRUD SQL — you just call methods.
 */
@Repository
public interface PortfolioRepository extends JpaRepository<Portfolio, UUID> {

    /**
     * Find the portfolio belonging to a specific user.
     * Used by: RiskEngine (check cash), GetPortfolio RPC, REST /api/portfolio/{userId}
     *
     * SQL generated: SELECT * FROM portfolios WHERE user_id = ?
     */
    Optional<Portfolio> findByUserId(UUID userId);
}
