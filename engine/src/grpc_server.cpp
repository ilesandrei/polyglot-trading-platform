// ═══════════════════════════════════════════════════════════════
//  grpc_server.cpp — ExecutionService gRPC Implementation
//
//  Wraps the MatchingEngine in a gRPC server so the Java
//  Orchestrator can submit and cancel orders over the network.
//
//  Services exposed (port 50051):
//    SubmitOrder      — process one order, return status
//    CancelOrder      — cancel a resting order by ID
//    StreamExecutions — stream all matched trades in real-time
// ═══════════════════════════════════════════════════════════════

#include "matching_engine.hpp"

#include <grpcpp/grpcpp.h>
#include "services.grpc.pb.h"
#include "trading.pb.h"

#include <iostream>
#include <mutex>
#include <vector>
#include <string>
#include <chrono>
#include <thread>

using grpc::Server;
using grpc::ServerBuilder;
using grpc::ServerContext;
using grpc::ServerWriter;
using grpc::Status;

// ─── Trade Broadcaster ────────────────────────────────────────────────────────
// Holds all active StreamExecutions writers so we can push trades to them.

class TradeBroadcaster {
public:
    // Register a new streaming client. Returns a token used to unregister.
    size_t subscribe(ServerWriter<trading::Trade>* writer) {
        std::lock_guard<std::mutex> lock(mutex_);
        size_t id = next_id_++;
        writers_[id] = writer;
        return id;
    }

    void unsubscribe(size_t id) {
        std::lock_guard<std::mutex> lock(mutex_);
        writers_.erase(id);
    }

    // Called from the matching engine callback — push trade to all subscribers.
    void broadcast(const trading::Trade& proto_trade) {
        std::lock_guard<std::mutex> lock(mutex_);
        for (auto& [id, writer] : writers_) {
            writer->Write(proto_trade);  // best-effort; ignores write errors
        }
    }

private:
    std::mutex mutex_;
    std::unordered_map<size_t, ServerWriter<trading::Trade>*> writers_;
    size_t next_id_ = 0;
};

// ─── Proto conversion helpers ─────────────────────────────────────────────────

static engine::Order proto_to_engine(const trading::Order& proto) {
    engine::Order o;
    o.order_id  = proto.order_id();
    o.user_id   = proto.user_id();
    o.symbol    = proto.symbol();
    o.quantity  = proto.quantity();
    o.price     = proto.price();
    o.filled_qty = 0.0;
    o.timestamp = proto.timestamp_ms();

    o.side = (proto.side() == trading::BUY) ? engine::Side::BUY : engine::Side::SELL;
    o.type = (proto.type() == trading::LIMIT) ? engine::Type::LIMIT : engine::Type::MARKET;
    o.status = engine::Status::PENDING;
    return o;
}

static trading::OrderStatus engine_status_to_proto(engine::Status s) {
    switch (s) {
        case engine::Status::PENDING:          return trading::PENDING;
        case engine::Status::PARTIALLY_FILLED: return trading::PARTIALLY_FILLED;
        case engine::Status::FILLED:           return trading::FILLED;
        case engine::Status::CANCELLED:        return trading::CANCELLED;
        case engine::Status::REJECTED:         return trading::REJECTED;
        default:                               return trading::ORDER_STATUS_UNSPECIFIED;
    }
}

static trading::Trade engine_trade_to_proto(const engine::Trade& t) {
    trading::Trade proto;
    proto.set_trade_id(t.trade_id);
    proto.set_buy_order_id(t.buy_order_id);
    proto.set_sell_order_id(t.sell_order_id);
    proto.set_symbol(t.symbol);
    proto.set_quantity(t.quantity);
    proto.set_price(t.price);
    proto.set_timestamp_ms(t.timestamp_ms);
    return proto;
}

// ─── ExecutionServiceImpl ─────────────────────────────────────────────────────

class ExecutionServiceImpl final : public trading::ExecutionService::Service {
public:
    ExecutionServiceImpl()
        : engine_([this](const engine::Trade& t) {
            // Called by matching engine on every match — forward to all gRPC streams
            trading::Trade proto = engine_trade_to_proto(t);

            // Log to stdout
            std::cout << "[TRADE] " << t.symbol
                      << " qty=" << t.quantity
                      << " @ "   << t.price
                      << " | buy=" << t.buy_order_id
                      << " sell=" << t.sell_order_id
                      << std::endl;

            broadcaster_.broadcast(proto);
        }) {}

    // ── SubmitOrder ──────────────────────────────────────────────
    Status SubmitOrder(ServerContext* /*ctx*/,
                       const trading::SubmitOrderRequest* request,
                       trading::SubmitOrderResponse* response) override {
        if (request->order().order_id().empty()) {
            response->set_message("Missing order_id in request");
            response->set_status(trading::REJECTED);
            return Status::OK;
        }

        engine::Order order = proto_to_engine(request->order());

        std::cout << "[ORDER] Received " << order.symbol
                  << " " << (order.side == engine::Side::BUY ? "BUY" : "SELL")
                  << " qty=" << order.quantity
                  << " @ "   << order.price
                  << std::endl;

        engine::Order result = engine_.process_order(order);

        response->set_order_id(result.order_id);
        response->set_status(engine_status_to_proto(result.status));
        response->set_message("Order processed");
        return Status::OK;
    }

    // ── CancelOrder ──────────────────────────────────────────────
    Status CancelOrder(ServerContext* /*ctx*/,
                       const trading::CancelOrderRequest* request,
                       trading::CancelOrderResponse* response) override {
        // The proto has order_id but not symbol — scan all known books.
        // In Phase 3, we can extend the proto or pass symbol via gRPC metadata.
        const std::string& order_id = request->order_id();
        bool cancelled = engine_.cancel_any(order_id);
        response->set_success(cancelled);
        response->set_message(cancelled ? "Order cancelled" : "Order not found");
        return Status::OK;
    }

    // ── StreamExecutions ─────────────────────────────────────────
    Status StreamExecutions(ServerContext* ctx,
                            const trading::StreamExecutionsRequest* /*request*/,
                            ServerWriter<trading::Trade>* writer) override {
        size_t sub_id = broadcaster_.subscribe(writer);
        std::cout << "[STREAM] Client subscribed (id=" << sub_id << ")" << std::endl;

        // Block here until the client disconnects
        while (!ctx->IsCancelled()) {
            std::this_thread::sleep_for(std::chrono::milliseconds(100));
        }

        broadcaster_.unsubscribe(sub_id);
        std::cout << "[STREAM] Client disconnected (id=" << sub_id << ")" << std::endl;
        return Status::OK;
    }

private:
    engine::MatchingEngine engine_;
    TradeBroadcaster       broadcaster_;
};

// ─── main ─────────────────────────────────────────────────────────────────────

int main() {
    const std::string address = "0.0.0.0:50051";

    ExecutionServiceImpl service;

    ServerBuilder builder;
    builder.AddListeningPort(address, grpc::InsecureServerCredentials());
    builder.RegisterService(&service);

    std::unique_ptr<Server> server = builder.BuildAndStart();
    std::cout << "[ENGINE] gRPC server listening on " << address << std::endl;
    server->Wait();
    return 0;
}
