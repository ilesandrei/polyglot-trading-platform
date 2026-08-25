# Algorithmic Trading Platform

A high-performance, polyglot algorithmic trading simulator built with **C++**, **Java**, and **Python** communicating over **gRPC + Protobuf**.

## Architecture

```
Python Strategy ──gRPC──► Java Orchestrator ──gRPC──► C++ Matching Engine
                                  │
                            PostgreSQL DB
                                  │
                         REST / WebSocket API
```

## Tech Stack

| Layer | Language | Role |
|---|---|---|
| Signal Generation | Python | Market data + quant strategy |
| Orchestration | Java (Spring Boot) | Risk checks, routing, API |
| Execution Engine | C++ | Low-latency order matching |
| Communication | Protobuf + gRPC | Cross-language RPC |
| Persistence | PostgreSQL | Users, portfolios, trades |
| Infrastructure | Docker Compose | Service orchestration |

## Roadmap

See [`polyglot_trading_roadmap.md`](./polyglot_trading_roadmap.md) for the full 12-week plan.

## Project Structure

```
proto/          # Shared Protobuf schemas & gRPC service definitions
```
