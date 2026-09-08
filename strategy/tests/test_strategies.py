"""
Unit Test Suite for Python Quantitative Strategy Engine.

Tests:
1. MarketDataFeed (OHLCV generation & tick simulation)
2. MovingAverageCrossover (Golden Cross, Death Cross, Hold)
3. RSIStrategy (Oversold, Overbought, Neutral)
"""

import sys
import unittest
from pathlib import Path
import pandas as pd
import numpy as np

# Ensure strategy root is in sys.path
strategy_root = Path(__file__).resolve().parent.parent
if str(strategy_root) not in sys.path:
    sys.path.insert(0, str(strategy_root))

from src.data.market_data import MarketDataFeed
from src.strategies.base_strategy import SignalAction
from src.strategies.ma_crossover import MovingAverageCrossover
from src.strategies.rsi_strategy import RSIStrategy


class TestMarketDataFeed(unittest.TestCase):
    """Unit tests for MarketDataFeed."""

    def setUp(self):
        self.feed = MarketDataFeed(use_mock=True)

    def test_mock_historical_structure(self):
        df = self.feed.fetch_historical("TEST_SYMBOL", period="1mo", interval="1d")
        self.assertFalse(df.empty)
        self.assertGreaterEqual(len(df), 30)
        expected_cols = ["Open", "High", "Low", "Close", "Volume"]
        for col in expected_cols:
            self.assertIn(col, df.columns)
            self.assertTrue((df[col] > 0).all(), f"All {col} values should be positive")

    def test_mock_tick_generation(self):
        tick = self.feed.generate_mock_tick("MSFT", base_price=100.0, volatility=0.02)
        self.assertEqual(tick["symbol"], "MSFT")
        self.assertIsInstance(tick["price"], float)
        self.assertGreater(tick["price"], 0.0)
        self.assertGreater(tick["quantity"], 0.0)
        self.assertGreater(tick["timestamp_ms"], 0)


class TestMovingAverageCrossover(unittest.TestCase):
    """Unit tests for MovingAverageCrossover strategy."""

    def test_insufficient_data_returns_hold(self):
        strat = MovingAverageCrossover(fast_period=5, slow_period=20)
        short_df = pd.DataFrame({"Close": [100.0] * 10})
        signal = strat.generate_signal("AAPL", short_df)
        self.assertEqual(signal.action, SignalAction.HOLD)
        self.assertEqual(signal.suggested_quantity, 0.0)

    def test_golden_cross_generates_buy(self):
        strat = MovingAverageCrossover(fast_period=2, slow_period=3, trade_quantity=5.0)
        # Bar 0: 10, Bar 1: 10, Bar 2: 9 (fast=9.5, slow=9.67 -> fast <= slow)
        # Bar 3: 15 (fast=12.0, slow=11.33 -> fast > slow) -> Golden Cross!
        df = pd.DataFrame({"Close": [10.0, 10.0, 9.0, 15.0]})
        signal = strat.generate_signal("AAPL", df)
        self.assertEqual(signal.action, SignalAction.BUY)
        self.assertEqual(signal.suggested_quantity, 5.0)
        self.assertEqual(signal.suggested_price, 15.0)
        self.assertGreater(signal.strength, 0.5)

    def test_death_cross_generates_sell(self):
        strat = MovingAverageCrossover(fast_period=2, slow_period=3, trade_quantity=5.0)
        # Bar 0: 15, Bar 1: 15, Bar 2: 16 (fast=15.5, slow=15.33 -> fast >= slow)
        # Bar 3: 10 (fast=13.0, slow=13.67 -> fast < slow) -> Death Cross!
        df = pd.DataFrame({"Close": [15.0, 15.0, 16.0, 10.0]})
        signal = strat.generate_signal("AAPL", df)
        self.assertEqual(signal.action, SignalAction.SELL)
        self.assertEqual(signal.suggested_quantity, 5.0)
        self.assertEqual(signal.suggested_price, 10.0)
        self.assertGreater(signal.strength, 0.5)

    def test_flat_trend_generates_hold(self):
        strat = MovingAverageCrossover(fast_period=2, slow_period=4)
        df = pd.DataFrame({"Close": [100.0, 100.0, 100.0, 100.0, 100.0]})
        signal = strat.generate_signal("AAPL", df)
        self.assertEqual(signal.action, SignalAction.HOLD)


class TestRSIStrategy(unittest.TestCase):
    """Unit tests for RSIStrategy."""

    def test_insufficient_data_returns_hold(self):
        strat = RSIStrategy(period=14)
        short_df = pd.DataFrame({"Close": [100.0] * 5})
        signal = strat.generate_signal("MSFT", short_df)
        self.assertEqual(signal.action, SignalAction.HOLD)

    def test_downtrend_oversold_generates_buy(self):
        strat = RSIStrategy(period=5, oversold_threshold=30.0, overbought_threshold=70.0, trade_quantity=8.0)
        # Strong consecutive drop pushes RSI well below 30
        df = pd.DataFrame({"Close": [100.0, 92.0, 85.0, 78.0, 70.0, 62.0, 55.0]})
        signal = strat.generate_signal("MSFT", df)
        self.assertEqual(signal.action, SignalAction.BUY)
        self.assertEqual(signal.suggested_quantity, 8.0)
        self.assertEqual(signal.suggested_price, 55.0)
        self.assertGreater(signal.strength, 0.0)

    def test_uptrend_overbought_generates_sell(self):
        strat = RSIStrategy(period=5, oversold_threshold=30.0, overbought_threshold=70.0, trade_quantity=8.0)
        # Strong consecutive rise pushes RSI well above 70
        df = pd.DataFrame({"Close": [50.0, 58.0, 65.0, 72.0, 80.0, 88.0, 95.0]})
        signal = strat.generate_signal("MSFT", df)
        self.assertEqual(signal.action, SignalAction.SELL)
        self.assertEqual(signal.suggested_quantity, 8.0)
        self.assertEqual(signal.suggested_price, 95.0)
        self.assertGreater(signal.strength, 0.0)

    def test_neutral_ranging_generates_hold(self):
        strat = RSIStrategy(period=5, oversold_threshold=30.0, overbought_threshold=70.0)
        # Alternating small variations keep RSI centered around 50
        df = pd.DataFrame({"Close": [100.0, 101.0, 99.5, 100.5, 99.8, 100.2, 100.0]})
        signal = strat.generate_signal("MSFT", df)
        self.assertEqual(signal.action, SignalAction.HOLD)


if __name__ == "__main__":
    unittest.main()
