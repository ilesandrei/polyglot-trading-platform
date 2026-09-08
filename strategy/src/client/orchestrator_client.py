"""
gRPC Client for Java Orchestrator.

Connects to OrchestratorService on port 50052 to submit orders and query portfolios.
"""

from typing import Optional, Dict, Any
import logging
import os
import time
import grpc

logger = logging.getLogger(__name__)


class OrchestratorClient:
    """
    Client for interacting with the Java Orchestrator gRPC server.
    """

    def __init__(self, target: Optional[str] = None):
        """
        Initialize the gRPC channel and service stub.

        Args:
            target: gRPC server host:port (defaults to ORCHESTRATOR_GRPC_TARGET or localhost:50052).
        """
        if target is None:
            host = os.getenv("ORCHESTRATOR_HOST", "localhost")
            port = os.getenv("ORCHESTRATOR_GRPC_PORT", "50052")
            target = f"{host}:{port}"

        self.target = target
        self.channel = None
        self.stub = None
        logger.info(f"[CLIENT] Initializing OrchestratorClient targeting {self.target}")

    def connect(self):
        """
        Open the insecure gRPC channel to the Java Orchestrator and initialize the stub.
        """
        if self.stub is None:
            self.channel = grpc.insecure_channel(self.target)
            from src.proto.services_pb2_grpc import OrchestratorServiceStub
            self.stub = OrchestratorServiceStub(self.channel)
            logger.info(f"[CLIENT] Connected to Java Orchestrator at {self.target}")

    def close(self):
        """
        Close the active gRPC channel.
        """
        if self.channel is not None:
            self.channel.close()
            self.channel = None
            self.stub = None
            logger.info("[CLIENT] Connection closed")

    def place_order(
        self,
        user_id: str,
        symbol: str,
        side: str,           # 'BUY' or 'SELL'
        order_type: str,     # 'LIMIT' or 'MARKET'
        quantity: float,
        price: float
    ) -> Any:
        """
        Submit a new order to the Java Orchestrator.

        Args:
            user_id: UUID of the account placing the order.
            symbol: Asset symbol (e.g. 'MSFT').
            side: 'BUY' or 'SELL'.
            order_type: 'LIMIT' or 'MARKET'.
            quantity: Number of units/shares.
            price: Order price.

        Returns:
            PlaceOrderResponse protobuf object with order_id, status, and message.
        """
        self.connect()
        from src.proto.trading_pb2 import Order, OrderSide, OrderType, OrderStatus
        from src.proto.services_pb2 import PlaceOrderRequest

        proto_side = OrderSide.BUY if side.upper() == "BUY" else OrderSide.SELL
        proto_type = OrderType.LIMIT if order_type.upper() == "LIMIT" else OrderType.MARKET

        order = Order(
            user_id=user_id,
            symbol=symbol,
            side=proto_side,
            type=proto_type,
            quantity=float(quantity),
            price=float(price),
            timestamp_ms=int(time.time() * 1000),
            status=OrderStatus.PENDING
        )

        request = PlaceOrderRequest(order=order)
        logger.info(f"[CLIENT] Sending PlaceOrder: {side} {quantity} {symbol} @ {price} for user {user_id}")
        return self.stub.PlaceOrder(request)

    def get_portfolio(self, user_id: str) -> Any:
        """
        Retrieve portfolio snapshot (cash and positions) for a user.

        Args:
            user_id: UUID string.

        Returns:
            Portfolio protobuf message.
        """
        self.connect()
        from src.proto.services_pb2 import GetPortfolioRequest

        request = GetPortfolioRequest(user_id=user_id)
        logger.info(f"[CLIENT] Fetching portfolio for user {user_id}")
        response = self.stub.GetPortfolio(request)
        return response.portfolio
