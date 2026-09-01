package com.trading.repository;

import com.trading.entity.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Spring Data repository for the `orders` table.
 */
@Repository
public interface OrderRepository extends JpaRepository<Order, UUID> {

    /**
     * Fetch all orders placed by a user, newest first.
     * Used by: REST GET /api/orders?userId=...
     *
     * SQL: SELECT * FROM orders WHERE user_id = ? ORDER BY created_at DESC
     */
    List<Order> findByUserIdOrderByCreatedAtDesc(UUID userId);

    /**
     * Fetch all orders for a specific symbol (any user).
     * Useful for admin/debugging views.
     */
    List<Order> findBySymbol(String symbol);
}
