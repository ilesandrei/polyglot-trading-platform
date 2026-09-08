"""
Strategy Engine Entry Point.

Orchestrates:
1. Ingesting market data (live or simulated).
2. Feeding bars to the selected quantitative strategy.
3. Transmitting generated BUY/SELL signals as orders to the Java Orchestrator.
"""

import os
import sys
import time
import logging
from pathlib import Path
from typing import Optional

# Ensure the 'strategy' package root is in sys.path regardless of execution directory
strategy_root = Path(__file__).resolve().parent.parent
if str(strategy_root) not in sys.path:
    sys.path.insert(0, str(strategy_root))

from src.data.market_data import MarketDataFeed
from src.strategies.base_strategy import SignalAction
from src.strategies.ma_crossover import MovingAverageCrossover
from src.strategies.rsi_strategy import RSIStrategy
from src.client.orchestrator_client import OrchestratorClient

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] [%(name)s] %(message)s"
)
logger = logging.getLogger("StrategyEngine")


def run_strategy_cycle(
    symbol: str,
    user_id: str,
    strategy,
    data_feed: MarketDataFeed,
    client: OrchestratorClient
):
    """
    Execute a single iteration of the strategy cycle.

    Args:
        symbol: Asset symbol to trade (e.g. 'MSFT').
        user_id: Account UUID placing the trades.
        strategy: An instance of BaseStrategy (e.g. MovingAverageCrossover).
        data_feed: MarketDataFeed instance.
        client: OrchestratorClient instance.
    """
    logger.info(f"[CYCLE] Evaluating {strategy.name} on {symbol} for user {user_id}")

    # 1. Fetch market data
    data = data_feed.fetch_historical(symbol=symbol, period="1mo", interval="1d")
    if data.empty:
        logger.warning(f"[CYCLE] No market data available for {symbol}. Skipping cycle.")
        return

    # 2. Evaluate strategy signal
    signal = strategy.generate_signal(symbol=symbol, data=data)
    logger.info(
        f"[SIGNAL] {signal.action.value} on {symbol} "
        f"(price: {signal.suggested_price:.2f}, qty: {signal.suggested_quantity}, strength: {signal.strength:.2f})"
    )

    # 3. If signal is BUY or SELL, dispatch order to Java Orchestrator
    if signal.action in (SignalAction.BUY, SignalAction.SELL):
        try:
            response = client.place_order(
                user_id=user_id,
                symbol=symbol,
                side=signal.action.value,
                order_type="LIMIT",
                quantity=signal.suggested_quantity,
                price=signal.suggested_price
            )
            logger.info(
                f"[ORDER SUCCESS] Order ID: {response.order_id}, Status: {response.status}, Message: {response.message}"
            )
        except Exception as e:
            logger.error(f"[ORDER FAILED] Could not dispatch order for {symbol}: {e}")

    # 4. If HOLD, log neutral status
    elif signal.action == SignalAction.HOLD:
        logger.info(f"[HOLD] Neutral regime. No order required for {symbol}.")


def main():
    """
    Main loop initializing components and running periodic evaluations.
    """
    user_id = os.getenv("TRADING_USER_ID", "00000000-0000-0000-0000-000000000001")
    symbol = os.getenv("TRADING_SYMBOL", "MSFT")
    strategy_name = os.getenv("TRADING_STRATEGY", "MA_CROSSOVER").upper()
    use_mock = os.getenv("USE_MOCK_DATA", "true").lower() == "true"
    interval_sec = int(os.getenv("CYCLE_INTERVAL_SECONDS", "5"))

    logger.info("==================================================")
    logger.info("  Polyglot Trading Platform - Strategy Engine     ")
    logger.info(f"  User ID:    {user_id}")
    logger.info(f"  Symbol:     {symbol}")
    logger.info(f"  Strategy:   {strategy_name}")
    logger.info(f"  Mock Data:  {use_mock}")
    logger.info(f"  Interval:   {interval_sec}s")
    logger.info("==================================================")

    # 1. Initialize data feed
    data_feed = MarketDataFeed(use_mock=use_mock)

    # 2. Instantiate strategy
    if strategy_name == "RSI":
        strategy = RSIStrategy(period=14, oversold_threshold=30.0, overbought_threshold=70.0)
    else:
        strategy = MovingAverageCrossover(fast_period=5, slow_period=20)

    # 3. Initialize gRPC client
    client = OrchestratorClient()
    try:
        client.connect()
    except Exception as e:
        logger.error(f"[STARTUP] Could not connect to Java Orchestrator: {e}")

    # 4. Run automated polling loop
    logger.info(f"[STARTED] Starting strategy loop (evaluating every {interval_sec}s). Press Ctrl+C to stop.")
    try:
        while True:
            try:
                run_strategy_cycle(symbol, user_id, strategy, data_feed, client)
            except Exception as e:
                logger.error(f"[ERROR] Strategy cycle exception: {e}")
            time.sleep(interval_sec)
    except KeyboardInterrupt:
        logger.info("[STOPPED] Strategy engine shutting down cleanly.")
    finally:
        client.close()


if __name__ == "__main__":
    main()
