package com.trading.rest;

import com.trading.entity.Order;
import com.trading.entity.Portfolio;
import com.trading.entity.Trade;
import com.trading.repository.OrderRepository;
import com.trading.repository.PortfolioRepository;
import com.trading.repository.TradeRepository;
import com.trading.service.RiskEngine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * TradingRestController — HTTP REST API for Phase 3.
 *
 * Exposes the platform to any HTTP client (browser, Postman, future frontend).
 * These endpoints call the same service layer as the gRPC server.
 *
 * Base path: /api
 *
 * Endpoints:
 *   POST   /api/orders                      — place a new order
 *   GET    /api/orders?userId={id}           — list all orders for a user
 *   DELETE /api/orders/{orderId}             — cancel an open order
 *   GET    /api/portfolio/{userId}           — get portfolio (cash + positions)
 *   GET    /api/trades?symbol={sym}          — recent trades for a symbol
 */
@Slf4j
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class TradingRestController {

    private final OrderRepository     orderRepository;
    private final PortfolioRepository portfolioRepository;
    private final TradeRepository     tradeRepository;
    private final RiskEngine          riskEngine;

    // ─────────────────────────────────────────────────────────────────────
    //  POST /api/orders — Place a new order
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Accepts an order as JSON, runs risk checks, and forwards it to the C++ engine.
     *
     * Request body (JSON):
     * {
     *   "userId":   "00000000-0000-0000-0000-000000000001",
     *   "symbol":   "AAPL",
     *   "side":     "BUY",
     *   "type":     "LIMIT",
     *   "quantity": 10,
     *   "price":    150.00
     * }
     *
     * Response (JSON):
     * {
     *   "orderId": "...",
     *   "status":  "PENDING",
     *   "message": "Order processed"
     * }
     */
    @PostMapping("/orders")
    public ResponseEntity<?> placeOrder(@RequestBody OrderRequest request) {
        // TODO: Validate required fields (userId, symbol, side, type, quantity).
        //       Return 400 Bad Request if anything is missing.

        // TODO: Build an Order entity from the request (same as in OrchestratorServiceImpl).

        // TODO: Run riskEngine.validate(order).
        //       Return 422 Unprocessable Entity if rejected, with the reason.

        // TODO: Persist the order, call the C++ engine via engineStub.submitOrder(),
        //       update the status, and return 200 OK with the result.
        //
        //       TIP: You can share logic with OrchestratorServiceImpl by extracting it
        //            into an OrderService that both the gRPC and REST layers call.

        throw new UnsupportedOperationException("TODO: implement POST /api/orders");
    }

    // ─────────────────────────────────────────────────────────────────────
    //  GET /api/orders?userId=... — List orders for a user
    // ─────────────────────────────────────────────────────────────────────

    @GetMapping("/orders")
    public ResponseEntity<List<Order>> getOrders(@RequestParam UUID userId) {
        // TODO: return orderRepository.findByUserIdOrderByCreatedAtDesc(userId)
        throw new UnsupportedOperationException("TODO: implement GET /api/orders");
    }

    // ─────────────────────────────────────────────────────────────────────
    //  DELETE /api/orders/{orderId} — Cancel an open order
    // ─────────────────────────────────────────────────────────────────────

    @DeleteMapping("/orders/{orderId}")
    public ResponseEntity<?> cancelOrder(@PathVariable UUID orderId) {
        // TODO:
        //   1. Load the order from DB — return 404 if not found.
        //   2. Forward a CancelOrder RPC to the C++ engine.
        //   3. If the engine confirms cancellation, update order status to CANCELLED in DB.
        //   4. Return 200 OK or 409 Conflict if already filled.

        throw new UnsupportedOperationException("TODO: implement DELETE /api/orders/{orderId}");
    }

    // ─────────────────────────────────────────────────────────────────────
    //  GET /api/portfolio/{userId}
    // ─────────────────────────────────────────────────────────────────────

    @GetMapping("/portfolio/{userId}")
    public ResponseEntity<?> getPortfolio(@PathVariable UUID userId) {
        // TODO:
        //   1. Load portfolio from portfolioRepository.findByUserId(userId)
        //   2. Load positions for this portfolio
        //   3. Return a combined JSON response (Portfolio + list of Positions)
        //
        //   TIP: Create a PortfolioResponse DTO to shape the JSON cleanly.

        throw new UnsupportedOperationException("TODO: implement GET /api/portfolio/{userId}");
    }

    // ─────────────────────────────────────────────────────────────────────
    //  GET /api/trades?symbol=AAPL
    // ─────────────────────────────────────────────────────────────────────

    @GetMapping("/trades")
    public ResponseEntity<List<Trade>> getTrades(@RequestParam String symbol) {
        // TODO: return tradeRepository.findBySymbolOrderByExecutedAtDesc(symbol)
        throw new UnsupportedOperationException("TODO: implement GET /api/trades");
    }

    // ─────────────────────────────────────────────────────────────────────
    //  Request DTO
    // ─────────────────────────────────────────────────────────────────────

    /**
     * JSON body for POST /api/orders.
     * Use a record for brevity — Spring's Jackson deserializes it automatically.
     */
    public record OrderRequest(
        UUID   userId,
        String symbol,
        String side,      // "BUY" or "SELL"
        String type,      // "LIMIT" or "MARKET"
        double quantity,
        double price      // ignored for MARKET orders
    ) {}
}
