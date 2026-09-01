package com.trading.repository;

import com.trading.entity.Position;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data repository for the `positions` table.
 */
@Repository
public interface PositionRepository extends JpaRepository<Position, UUID> {

    /**
     * Find all asset positions held by a portfolio.
     * Used by: GetPortfolio RPC to build the holdings list.
     *
     * SQL: SELECT * FROM positions WHERE portfolio_id = ?
     */
    List<Position> findByPortfolioId(UUID portfolioId);

    /**
     * Find a specific symbol position within a portfolio.
     * Used by: RiskEngine (check SELL eligibility), portfolio update after trade.
     *
     * SQL: SELECT * FROM positions WHERE portfolio_id = ? AND symbol = ?
     */
    Optional<Position> findByPortfolioIdAndSymbol(UUID portfolioId, String symbol);
}
