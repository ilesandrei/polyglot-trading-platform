"""
Relative Strength Index (RSI) Mean-Reversion Strategy.

Generates:
- BUY signal: RSI drops below oversold threshold (default 30) and starts rebounding.
- SELL signal: RSI rises above overbought threshold (default 70) and starts retreating.
- HOLD: RSI remains between oversold and overbought bounds.
"""

import logging
import pandas as pd
import numpy as np
from .base_strategy import BaseStrategy, TradeSignal, SignalAction

logger = logging.getLogger(__name__)


class RSIStrategy(BaseStrategy):
    """
    Relative Strength Index (RSI) momentum and mean-reversion trading strategy.
    """

    def __init__(
        self,
        period: int = 14,
        oversold_threshold: float = 30.0,
        overbought_threshold: float = 70.0,
        trade_quantity: float = 10.0
    ):
        """
        Initialize the RSI strategy parameters.

        Args:
            period: Lookback window for RSI calculation (default 14).
            oversold_threshold: RSI value below which an asset is considered oversold (default 30.0).
            overbought_threshold: RSI value above which an asset is considered overbought (default 70.0).
            trade_quantity: Quantity to buy or sell when trigger fires.
        """
        super().__init__(name="RSI_STRATEGY")
        self.period = period
        self.oversold_threshold = oversold_threshold
        self.overbought_threshold = overbought_threshold
        self.trade_quantity = trade_quantity

    def calculate_rsi(self, data: pd.DataFrame) -> pd.Series:
        """
        Calculate the Wilder's / Exponential Relative Strength Index (RSI).

        Formula:
            Delta = Close[t] - Close[t-1]
            Gain = Delta if Delta > 0 else 0
            Loss = -Delta if Delta < 0 else 0
            RS = AvgGain / AvgLoss
            RSI = 100 - (100 / (1 + RS))

        Args:
            data: DataFrame containing 'Close' column.

        Returns:
            pd.Series with RSI values between 0.0 and 100.0.
        """
        if "Close" not in data.columns:
            raise ValueError("Data must contain 'Close' column for RSI calculation")

        if len(data) < self.period + 1:
            raise ValueError(f"Need at least {self.period + 1} bars to calculate RSI")

        delta = data["Close"].diff()
        gain = delta.clip(lower=0)
        loss = -1.0 * delta.clip(upper=0)

        # Wilder's smoothing with exponential moving average
        avg_gain = gain.ewm(alpha=1.0 / self.period, min_periods=self.period, adjust=False).mean()
        avg_loss = loss.ewm(alpha=1.0 / self.period, min_periods=self.period, adjust=False).mean()

        # Handle zero division safely
        rs = avg_gain / avg_loss.replace(0, np.nan)
        rsi = 100.0 - (100.0 / (1.0 + rs))
        # If avg_loss is 0, RSI is 100
        rsi = rsi.fillna(100.0)
        return rsi

    def generate_signal(self, symbol: str, data: pd.DataFrame) -> TradeSignal:
        """
        Evaluate recent RSI values for mean-reversion trading opportunities.

        Args:
            symbol: Ticker symbol.
            data: OHLCV DataFrame.

        Returns:
            TradeSignal with action BUY, SELL, or HOLD.
        """
        if len(data) < self.period + 1:
            logger.warning(
                f"[STRATEGY] Insufficient data ({len(data)} bars) for RSI period={self.period}. Returning HOLD."
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

        rsi = self.calculate_rsi(data)
        valid_rsi = rsi.dropna()

        if valid_rsi.empty:
            price = float(data["Close"].iloc[-1])
            return TradeSignal(
                symbol=symbol,
                action=SignalAction.HOLD,
                strategy_name=self.name,
                suggested_price=price,
                suggested_quantity=0.0,
                strength=0.0
            )

        curr_rsi = float(valid_rsi.iloc[-1])
        curr_price = float(data["Close"].iloc[-1])

        # Oversold regime -> BUY
        if curr_rsi < self.oversold_threshold:
            strength = min(1.0, max(0.1, (self.oversold_threshold - curr_rsi) / self.oversold_threshold))
            logger.info(f"[STRATEGY] Oversold RSI={curr_rsi:.1f} on {symbol} (< {self.oversold_threshold}). BUY signal.")
            return TradeSignal(
                symbol=symbol,
                action=SignalAction.BUY,
                strategy_name=self.name,
                suggested_price=curr_price,
                suggested_quantity=self.trade_quantity,
                strength=strength
            )

        # Overbought regime -> SELL
        if curr_rsi > self.overbought_threshold:
            strength = min(1.0, max(0.1, (curr_rsi - self.overbought_threshold) / (100.0 - self.overbought_threshold)))
            logger.info(f"[STRATEGY] Overbought RSI={curr_rsi:.1f} on {symbol} (> {self.overbought_threshold}). SELL signal.")
            return TradeSignal(
                symbol=symbol,
                action=SignalAction.SELL,
                strategy_name=self.name,
                suggested_price=curr_price,
                suggested_quantity=self.trade_quantity,
                strength=strength
            )

        # Neutral zone -> HOLD
        return TradeSignal(
            symbol=symbol,
            action=SignalAction.HOLD,
            strategy_name=self.name,
            suggested_price=curr_price,
            suggested_quantity=0.0,
            strength=0.0
        )
