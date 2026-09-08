"""
Base Strategy Interface and Signal Data Structures.
"""

from abc import ABC, abstractmethod
from dataclasses import dataclass
from enum import Enum
from typing import Optional
import time
import pandas as pd


class SignalAction(Enum):
    """Trading signal action."""
    HOLD = "HOLD"
    BUY = "BUY"
    SELL = "SELL"


@dataclass
class TradeSignal:
    """
    Standard trading signal emitted by quantitative strategies.
    """
    symbol: str
    action: SignalAction
    strategy_name: str
    suggested_price: float
    suggested_quantity: float
    strength: float = 1.0          # Confidence score (0.0 to 1.0)
    timestamp_ms: int = 0

    def __post_init__(self):
        if self.timestamp_ms == 0:
            self.timestamp_ms = int(time.time() * 1000)


class BaseStrategy(ABC):
    """
    Abstract Base Class that all quantitative trading strategies must inherit from.
    """

    def __init__(self, name: str):
        self.name = name

    @abstractmethod
    def generate_signal(self, symbol: str, data: pd.DataFrame) -> TradeSignal:
        """
        Analyze incoming market data and produce a TradeSignal.

        Args:
            symbol: Ticker symbol (e.g. 'MSFT').
            data: Historical or live OHLCV pandas DataFrame.

        Returns:
            TradeSignal with action BUY, SELL, or HOLD.
        """
        pass
