"""
Moving Average Crossover Strategy.

Generates:
- BUY signal (Golden Cross): Fast moving average crosses above Slow moving average.
- SELL signal (Death Cross): Fast moving average crosses below Slow moving average.
- HOLD: No crossover event on the latest bar.
"""

import logging
import pandas as pd
from .base_strategy import BaseStrategy, TradeSignal, SignalAction

logger = logging.getLogger(__name__)


class MovingAverageCrossover(BaseStrategy):
    """
    Moving Average Crossover trading strategy using Pandas rolling windows.
    """

    def __init__(
        self,
        fast_period: int = 5,
        slow_period: int = 20,
        trade_quantity: float = 10.0
    ):
        """
        Initialize the strategy parameters.

        Args:
            fast_period: Number of periods for the fast SMA (e.g. 5 or 10).
            slow_period: Number of periods for the slow SMA (e.g. 20 or 50).
            trade_quantity: Number of units to buy/sell when a signal triggers.
        """
        super().__init__(name="MA_CROSSOVER")
        self.fast_period = fast_period
        self.slow_period = slow_period
        self.trade_quantity = trade_quantity

    def calculate_indicators(self, data: pd.DataFrame) -> pd.DataFrame:
        """
        Calculate fast and slow Simple Moving Averages on the 'Close' price series.

        Args:
            data: DataFrame containing at least the 'Close' column.

        Returns:
            DataFrame with added columns: 'fast_sma' and 'slow_sma'.
        """
        if "Close" not in data.columns:
            raise ValueError("Data must contain 'Close' column for MA Crossover")

        df = data.copy()
        df["fast_sma"] = df["Close"].rolling(window=self.fast_period).mean()
        df["slow_sma"] = df["Close"].rolling(window=self.slow_period).mean()
        return df

    def generate_signal(self, symbol: str, data: pd.DataFrame) -> TradeSignal:
        """
        Evaluate the latest two completed bars for a Moving Average Crossover event.

        Args:
            symbol: Ticker symbol (e.g. 'AAPL').
            data: OHLCV DataFrame.

        Returns:
            TradeSignal with action BUY, SELL, or HOLD.
        """
        if len(data) < self.slow_period:
            logger.warning(
                f"[STRATEGY] Insufficient data ({len(data)} bars) for slow_period={self.slow_period}. Returning HOLD."
            )
            price = float(data["Close"].iloc[-1]) if not data.empty and "Close" in data.columns else 0.0
            return TradeSignal(
                symbol=symbol,
                action=SignalAction.HOLD,
                strategy_name=self.name,
                suggested_price=price,
                suggested_quantity=0.0,
                strength=0.0
            )

        df = self.calculate_indicators(data)
        valid_df = df.dropna(subset=["fast_sma", "slow_sma"])

        if len(valid_df) < 2:
            price = float(data["Close"].iloc[-1])
            return TradeSignal(
                symbol=symbol,
                action=SignalAction.HOLD,
                strategy_name=self.name,
                suggested_price=price,
                suggested_quantity=0.0,
                strength=0.0
            )

        prev = valid_df.iloc[-2]
        curr = valid_df.iloc[-1]
        curr_price = float(curr["Close"])

        # Golden Cross: Fast SMA crosses above Slow SMA -> BUY
        if prev["fast_sma"] <= prev["slow_sma"] and curr["fast_sma"] > curr["slow_sma"]:
            logger.info(f"[STRATEGY] Golden Cross detected on {symbol} @ {curr_price:.2f}")
            return TradeSignal(
                symbol=symbol,
                action=SignalAction.BUY,
                strategy_name=self.name,
                suggested_price=curr_price,
                suggested_quantity=self.trade_quantity,
                strength=0.9
            )

        # Death Cross: Fast SMA crosses below Slow SMA -> SELL
        if prev["fast_sma"] >= prev["slow_sma"] and curr["fast_sma"] < curr["slow_sma"]:
            logger.info(f"[STRATEGY] Death Cross detected on {symbol} @ {curr_price:.2f}")
            return TradeSignal(
                symbol=symbol,
                action=SignalAction.SELL,
                strategy_name=self.name,
                suggested_price=curr_price,
                suggested_quantity=self.trade_quantity,
                strength=0.9
            )

        # No crossover -> HOLD
        return TradeSignal(
            symbol=symbol,
            action=SignalAction.HOLD,
            strategy_name=self.name,
            suggested_price=curr_price,
            suggested_quantity=0.0,
            strength=0.0
        )
