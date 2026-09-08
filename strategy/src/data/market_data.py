"""
Market Data Module.

Provides:
1. Real-world OHLCV historical candle retrieval (e.g. Yahoo Finance).
2. Simulated random-walk tick generation for offline/closed-market development.
"""

from typing import Optional, Dict, Any
import logging
import random
import time
import pandas as pd

try:
    import yfinance as yf
except ImportError:
    yf = None

logger = logging.getLogger(__name__)


class MarketDataFeed:
    """
    Handles fetching and streaming market data for trading strategies.
    """

    def __init__(self, use_mock: bool = False):
        """
        Initialize the market data feed.

        Args:
            use_mock: If True, uses random-walk simulation instead of live network calls.
        """
        self.use_mock = use_mock
        self._mock_prices: Dict[str, float] = {}

    def fetch_historical(
        self,
        symbol: str,
        period: str = "1mo",
        interval: str = "1d"
    ) -> pd.DataFrame:
        """
        Fetch historical OHLCV (Open, High, Low, Close, Volume) data for a given asset.

        Args:
            symbol: Ticker symbol (e.g. 'AAPL', 'MSFT', 'BTC-USD').
            period: Data period to download (e.g. '1d', '5d', '1mo', '1y').
            interval: Bar interval (e.g. '1m', '5m', '1h', '1d').

        Returns:
            pd.DataFrame with columns: ['Open', 'High', 'Low', 'Close', 'Volume'].
        """
        if self.use_mock:
            return self._generate_mock_historical(symbol, n_bars=30)

        try:
            ticker = yf.Ticker(symbol)
            df = ticker.history(period=period, interval=interval)
            if df.empty:
                logger.warning(f"[DATA] No data returned from Yahoo Finance for {symbol}. Falling back to mock data.")
                return self._generate_mock_historical(symbol, n_bars=30)
            
            # Ensure required columns are present and clean
            required_cols = ["Open", "High", "Low", "Close", "Volume"]
            for col in required_cols:
                if col not in df.columns:
                    raise ValueError(f"Missing column {col} in fetched data")
            
            return df[required_cols]
        except Exception as e:
            logger.warning(f"[DATA] Error fetching historical data for {symbol}: {e}. Falling back to mock data.")
            return self._generate_mock_historical(symbol, n_bars=30)

    def generate_mock_tick(
        self,
        symbol: str,
        base_price: float = 150.0,
        volatility: float = 0.01
    ) -> Dict[str, Any]:
        """
        Generate a single simulated price tick using a random-walk model.

        Args:
            symbol: Ticker symbol.
            base_price: Starting price if this is the first tick for the symbol.
            volatility: Maximum percentage price change per tick (e.g. 0.01 = 1%).

        Returns:
            Dict with 'symbol', 'price', 'quantity', 'timestamp_ms'.
        """
        prev_price = self._mock_prices.get(symbol, base_price)
        delta = random.uniform(-volatility, volatility)
        new_price = round(prev_price * (1 + delta), 2)
        self._mock_prices[symbol] = new_price

        return {
            "symbol": symbol,
            "price": new_price,
            "quantity": float(random.randint(1, 10)),
            "timestamp_ms": int(time.time() * 1000)
        }

    def _generate_mock_historical(self, symbol: str, n_bars: int = 50) -> pd.DataFrame:
        """
        Internal helper: Generate synthetic OHLCV bars for offline unit testing.
        """
        base_price = self._mock_prices.get(symbol, 150.0)
        prices = [base_price]
        for _ in range(n_bars - 1):
            change = random.uniform(-0.02, 0.02)  # +/- 2% daily fluctuation
            prices.append(round(prices[-1] * (1 + change), 2))

        data = []
        for close in prices:
            open_p = round(close * (1 + random.uniform(-0.005, 0.005)), 2)
            high_p = round(max(open_p, close) * (1 + random.uniform(0.001, 0.01)), 2)
            low_p = round(min(open_p, close) * (1 - random.uniform(0.001, 0.01)), 2)
            vol = random.randint(1000, 50000)
            data.append({
                "Open": open_p,
                "High": high_p,
                "Low": low_p,
                "Close": close,
                "Volume": vol
            })

        self._mock_prices[symbol] = prices[-1]
        return pd.DataFrame(data)
