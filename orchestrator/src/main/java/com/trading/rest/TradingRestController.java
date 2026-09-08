package com.trading.rest;

import com.trading.entity.Order;
import com.trading.entity.Portfolio;
import com.trading.entity.Position;
import com.trading.entity.Trade;
import com.trading.repository.OrderRepository;
import com.trading.repository.PortfolioRepository;
import com.trading.repository.PositionRepository;
import com.trading.repository.TradeRepository;
import com.trading.service.RiskEngine;
import com.trading.service.RiskEngine.RiskResult;

import com.trading.proto.CancelOrderRequest;
import com.trading.proto.CancelOrderResponse;
import com.trading.proto.ExecutionServiceGrpc;
import com.trading.proto.OrderSide;
import com.trading.proto.OrderStatus;
import com.trading.proto.OrderType;
import com.trading.proto.SubmitOrderRequest;
import com.trading.proto.SubmitOrderResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * TradingRestController — HTTP REST API for Phase 3.
 *
 * Exposes the platform to any HTTP client (browser, Postman, future frontend).
 * Base path: /api
 */
@Slf4j
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Tag(name = "Trading Platform API", description = "Endpoints for order management, portfolio queries, and market trade history")
public class TradingRestController {

    private final OrderRepository     orderRepository;
    private final PortfolioRepository portfolioRepository;
    private final PositionRepository  positionRepository;
    private final TradeRepository     tradeRepository;
    private final RiskEngine          riskEngine;

    @GrpcClient("cpp-engine")
    private ExecutionServiceGrpc.ExecutionServiceBlockingStub engineStub;

    // ─────────────────────────────────────────────────────────────────────
    //  POST /api/orders — Place a new order
    // ─────────────────────────────────────────────────────────────────────

    @Operation(summary = "Place a new order", description = "Validates against user's portfolio via RiskEngine and forwards to C++ engine.")
    @PostMapping("/orders")
    public ResponseEntity<?> placeOrder(@RequestBody OrderRequest request) {
        if (request.userId() == null || request.symbol() == null || request.symbol().isBlank() ||
            request.side() == null || request.type() == null || request.quantity() <= 0) {
            return ResponseEntity.badRequest().body("Missing or invalid required order fields");
        }

        OrderSide sideProto;
        OrderType typeProto;
        try {
            sideProto = OrderSide.valueOf(request.side().toUpperCase());
            typeProto = OrderType.valueOf(request.type().toUpperCase());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body("Invalid side or type value");
        }

        UUID orderId = UUID.randomUUID();
        Order order = new Order();
        order.setId(orderId);
        order.setUserId(request.userId());
        order.setSymbol(request.symbol());
        order.setSide(sideProto.name());
        order.setType(typeProto.name());
        order.setQuantity(BigDecimal.valueOf(request.quantity()));
        order.setPrice(BigDecimal.valueOf(request.price()));
        order.setStatus("PENDING");
        order.setCreatedAt(OffsetDateTime.now());
        order.setUpdatedAt(OffsetDateTime.now());

        orderRepository.save(order);

        RiskResult risk = riskEngine.validate(order);
        if (!risk.approved()) {
            order.setStatus("REJECTED");
            order.setUpdatedAt(OffsetDateTime.now());
            orderRepository.save(order);
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(new OrderResponse(orderId, "REJECTED", "Risk check failed: " + risk.reason()));
        }

        order.setStatus("VALIDATED");
        order.setUpdatedAt(OffsetDateTime.now());
        orderRepository.save(order);

        com.trading.proto.Order engineOrder = com.trading.proto.Order.newBuilder()
            .setOrderId(orderId.toString())
            .setUserId(order.getUserId().toString())
            .setSymbol(order.getSymbol())
            .setSide(sideProto)
            .setType(typeProto)
            .setQuantity(request.quantity())
            .setPrice(request.price())
            .setTimestampMs(System.currentTimeMillis())
            .setStatus(OrderStatus.VALIDATED)
            .build();

        SubmitOrderRequest engineRequest = SubmitOrderRequest.newBuilder()
            .setOrder(engineOrder)
            .build();

        try {
            SubmitOrderResponse engineResponse = engineStub.submitOrder(engineRequest);
            order.setStatus(engineResponse.getStatus().name());
            order.setUpdatedAt(OffsetDateTime.now());
            orderRepository.save(order);

            return ResponseEntity.ok(new OrderResponse(orderId, engineResponse.getStatus().name(), engineResponse.getMessage()));
        } catch (Exception e) {
            log.error("[REST] Engine submit failed for order {}: {}", orderId, e.getMessage());
            order.setStatus("REJECTED");
            order.setUpdatedAt(OffsetDateTime.now());
            orderRepository.save(order);

            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(new OrderResponse(orderId, "REJECTED", "Engine unavailable: " + e.getMessage()));
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    //  GET /api/orders?userId=... — List orders for a user
    // ─────────────────────────────────────────────────────────────────────

    @Operation(summary = "List orders for a user", description = "Returns all historical and open orders for the specified user UUID, ordered newest first.")
    @GetMapping("/orders")
    public ResponseEntity<List<Order>> getOrders(@RequestParam UUID userId) {
        return ResponseEntity.ok(orderRepository.findByUserIdOrderByCreatedAtDesc(userId));
    }

    // ─────────────────────────────────────────────────────────────────────
    //  DELETE /api/orders/{orderId} — Cancel an open order
    // ─────────────────────────────────────────────────────────────────────

    @Operation(summary = "Cancel an open order", description = "Sends a cancel request to the C++ matching engine and updates status in DB.")
    @DeleteMapping("/orders/{orderId}")
    public ResponseEntity<?> cancelOrder(@PathVariable UUID orderId) {
        Optional<Order> orderOpt = orderRepository.findById(orderId);
        if (orderOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Order order = orderOpt.get();
        if ("FILLED".equals(order.getStatus()) || "CANCELLED".equals(order.getStatus())) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                .body("Order cannot be cancelled in state: " + order.getStatus());
        }

        try {
            CancelOrderRequest cancelReq = CancelOrderRequest.newBuilder()
                .setOrderId(order.getId().toString())
                .setUserId(order.getUserId().toString())
                .build();

            CancelOrderResponse cancelRes = engineStub.cancelOrder(cancelReq);
            if (cancelRes.getSuccess()) {
                order.setStatus("CANCELLED");
                order.setUpdatedAt(OffsetDateTime.now());
                orderRepository.save(order);
                return ResponseEntity.ok("Order cancelled successfully");
            } else {
                return ResponseEntity.status(HttpStatus.CONFLICT).body(cancelRes.getMessage());
            }
        } catch (Exception e) {
            log.error("[REST] Cancel error for order {}: {}", orderId, e.getMessage());
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body("Engine unavailable: " + e.getMessage());
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    //  GET /api/portfolio/{userId}
    // ─────────────────────────────────────────────────────────────────────

    @Operation(summary = "Get user portfolio snapshot", description = "Returns cash balance and all asset position holdings for the user.")
    @GetMapping("/portfolio/{userId}")
    public ResponseEntity<?> getPortfolio(@PathVariable UUID userId) {
        Optional<Portfolio> portfolioOpt = portfolioRepository.findByUserId(userId);
        if (portfolioOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Portfolio portfolio = portfolioOpt.get();
        List<Position> positions = positionRepository.findByPortfolioId(portfolio.getId());
        return ResponseEntity.ok(new PortfolioResponse(portfolio.getUserId(), portfolio.getCash(), positions));
    }

    // ─────────────────────────────────────────────────────────────────────
    //  GET /api/trades?symbol=AAPL
    // ─────────────────────────────────────────────────────────────────────

    @Operation(summary = "Get executed trades for a symbol", description = "Returns all matched executions for the given symbol, newest first.")
    @GetMapping("/trades")
    public ResponseEntity<List<Trade>> getTrades(@RequestParam String symbol) {
        return ResponseEntity.ok(tradeRepository.findBySymbolOrderByExecutedAtDesc(symbol));
    }

    // ─────────────────────────────────────────────────────────────────────
    //  DTOs
    // ─────────────────────────────────────────────────────────────────────

    public record OrderRequest(
        UUID   userId,
        String symbol,
        String side,      // "BUY" or "SELL"
        String type,      // "LIMIT" or "MARKET"
        double quantity,
        double price      // ignored for MARKET orders
    ) {}

    public record OrderResponse(
        UUID   orderId,
        String status,
        String message
    ) {}

    public record PortfolioResponse(
        UUID           userId,
        BigDecimal     cash,
        List<Position> positions
    ) {}
}
