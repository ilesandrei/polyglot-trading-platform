package com.trading.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for fetching live financial market quotes and crypto data
 * with in-memory caching to prevent upstream rate limiting.
 */
@Service
public class MarketDataService {

    private static final Logger log = LoggerFactory.getLogger(MarketDataService.class);

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final Map<String, CachedQuote> quoteCache = new ConcurrentHashMap<>();
    private static final long CACHE_TTL_MS = 4000; // 4 second cache

    public record MarketQuote(
        String symbol,
        String name,
        String category, // "STOCK" or "CRYPTO"
        double price,
        double changePercent,
        double dayHigh,
        double dayLow,
        long volume,
        long marketCap,
        long timestampMs
    ) {}

    public record CandleData(
        long time,
        double open,
        double high,
        double low,
        double close,
        long volume
    ) {}

    public record HistoryResponse(
        String symbol,
        String range,
        String interval,
        List<CandleData> candles
    ) {}

    public record AssetSummary(
        String symbol,
        String name,
        String category,
        String icon
    ) {}

    private record CachedQuote(MarketQuote quote, long cachedAt) {}
    private record CachedHistory(HistoryResponse response, long cachedAt) {}

    private final Map<String, CachedHistory> historyCache = new ConcurrentHashMap<>();
    private static final long HISTORY_CACHE_TTL_MS = 15000; // 15-second cache for candle history

    public MarketDataService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(4))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    }

    /**
     * Curated catalog of popular stocks and Robinhood crypto/memecoins.
     */
    public List<AssetSummary> getCatalog() {
        return List.of(
            // Crypto & Memecoins
            new AssetSummary("ETH-USD", "Ethereum", "CRYPTO", "🔷"),
            new AssetSummary("BTC-USD", "Bitcoin", "CRYPTO", "₿"),
            new AssetSummary("SOL-USD", "Solana", "CRYPTO", "🟣"),
            new AssetSummary("DOGE-USD", "Dogecoin", "CRYPTO", "🐶"),
            new AssetSummary("SHIB-USD", "Shiba Inu", "CRYPTO", "🐕"),
            new AssetSummary("PEPE-USD", "Pepe", "CRYPTO", "🐸"),

            // Top Stocks
            new AssetSummary("NVDA", "NVIDIA Corp", "STOCK", "🟩"),
            new AssetSummary("TSLA", "Tesla Inc", "STOCK", "🚗"),
            new AssetSummary("AAPL", "Apple Inc", "STOCK", "🍎"),
            new AssetSummary("MSFT", "Microsoft Corp", "STOCK", "🪟"),
            new AssetSummary("AMZN", "Amazon.com Inc", "STOCK", "📦")
        );
    }

    /**
     * Fetch real-time market quote for any stock or crypto symbol.
     */
    public MarketQuote getQuote(String symbol) {
        String cleanSymbol = normalizeSymbol(symbol);

        // Check cache
        CachedQuote cached = quoteCache.get(cleanSymbol);
        long now = System.currentTimeMillis();
        if (cached != null && (now - cached.cachedAt) < CACHE_TTL_MS) {
            return cached.quote;
        }

        try {
            String url = "https://query1.finance.yahoo.com/v8/finance/chart/" + cleanSymbol + "?interval=1d&range=1d";
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                .timeout(Duration.ofSeconds(4))
                .GET()
                .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                JsonNode root = objectMapper.readTree(response.body());
                JsonNode result = root.path("chart").path("result").get(0);
                if (result != null) {
                    JsonNode meta = result.path("meta");
                    double price = meta.path("regularMarketPrice").asDouble();
                    double changePct = meta.path("regularMarketChangePercent").asDouble();
                    double high = meta.path("regularMarketDayHigh").asDouble(price);
                    double low = meta.path("regularMarketDayLow").asDouble(price);
                    long volume = meta.path("regularMarketVolume").asLong(0);
                    long yahooMarketCap = meta.path("marketCap").asLong(0);
                    String shortName = meta.path("shortName").asText(cleanSymbol);
                    String category = cleanSymbol.contains("-USD") || cleanSymbol.contains("-") ? "CRYPTO" : "STOCK";

                    long finalMarketCap = estimateMarketCap(cleanSymbol, price, yahooMarketCap);

                    MarketQuote quote = new MarketQuote(
                        cleanSymbol,
                        shortName,
                        category,
                        roundPrice(price),
                        round(changePct, 2),
                        roundPrice(high),
                        roundPrice(low),
                        volume,
                        finalMarketCap,
                        now
                    );

                    quoteCache.put(cleanSymbol, new CachedQuote(quote, now));
                    return quote;
                }
            }
        } catch (Exception e) {
            log.warn("[MARKET] Live quote failed for {}: {}. Falling back to estimated quote.", cleanSymbol, e.getMessage());
        }

        // Fallback estimated quote if Yahoo Finance is unreachable or rate limited
        return quoteCache.computeIfAbsent(cleanSymbol, sym -> {
            boolean isCrypto = sym.contains("-");
            double fallbackPrice = getBaseFallbackPrice(sym);
            long fallbackMcap = estimateMarketCap(sym, fallbackPrice, 0);
            return new CachedQuote(new MarketQuote(
                sym, sym + " (Estimated)", isCrypto ? "CRYPTO" : "STOCK",
                fallbackPrice, 0.5, fallbackPrice * 1.02, fallbackPrice * 0.98,
                500000L, fallbackMcap, now
            ), now);
        }).quote;
    }

    /**
     * Fetch historical OHLCV candle data for interactive charts.
     */
    public HistoryResponse getCandles(String symbol, String range, String interval) {
        String cleanSymbol = normalizeSymbol(symbol);
        String r = (range != null && !range.isBlank()) ? range.trim().toLowerCase() : "1d";
        String i = (interval != null && !interval.isBlank()) ? interval.trim().toLowerCase() : "5m";
        String cacheKey = cleanSymbol + ":" + r + ":" + i;

        long now = System.currentTimeMillis();
        CachedHistory cached = historyCache.get(cacheKey);
        if (cached != null && (now - cached.cachedAt) < HISTORY_CACHE_TTL_MS) {
            return cached.response;
        }

        List<CandleData> candles = new ArrayList<>();
        try {
            String url = String.format("https://query1.finance.yahoo.com/v8/finance/chart/%s?range=%s&interval=%s",
                cleanSymbol, r, i);
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                .timeout(Duration.ofSeconds(5))
                .GET()
                .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                JsonNode root = objectMapper.readTree(response.body());
                JsonNode result = root.path("chart").path("result").get(0);
                if (result != null) {
                    JsonNode timestamps = result.path("timestamp");
                    JsonNode quoteObj = result.path("indicators").path("quote").get(0);

                    if (timestamps != null && timestamps.isArray() && quoteObj != null) {
                        JsonNode opens = quoteObj.path("open");
                        JsonNode highs = quoteObj.path("high");
                        JsonNode lows = quoteObj.path("low");
                        JsonNode closes = quoteObj.path("close");
                        JsonNode volumes = quoteObj.path("volume");

                        int size = timestamps.size();
                        long prevVolume = 100000L;
                        for (int idx = 0; idx < size; idx++) {
                            long ts = timestamps.get(idx).asLong();
                            JsonNode oNode = opens.get(idx);
                            JsonNode hNode = highs.get(idx);
                            JsonNode lNode = lows.get(idx);
                            JsonNode cNode = closes.get(idx);
                            JsonNode vNode = volumes.get(idx);

                            if (oNode != null && !oNode.isNull() &&
                                hNode != null && !hNode.isNull() &&
                                lNode != null && !lNode.isNull() &&
                                cNode != null && !cNode.isNull()) {

                                double o = roundPrice(oNode.asDouble());
                                double h = roundPrice(hNode.asDouble());
                                double l = roundPrice(lNode.asDouble());
                                double c = roundPrice(cNode.asDouble());
                                long v = (vNode != null && !vNode.isNull()) ? vNode.asLong(0) : 0;
                                // Extrapolate volume if Yahoo reports 0 for recent bar
                                if (v <= 0) {
                                    v = prevVolume;
                                } else {
                                    prevVolume = v;
                                }

                                candles.add(new CandleData(ts, o, h, l, c, v));
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("[MARKET] Live candle fetch failed for {}: {}. Generating estimated candles.", cleanSymbol, e.getMessage());
        }

        if (candles.isEmpty()) {
            candles = generateFallbackCandles(cleanSymbol);
        }

        HistoryResponse history = new HistoryResponse(cleanSymbol, r, i, candles);
        historyCache.put(cacheKey, new CachedHistory(history, now));
        return history;
    }

    private List<CandleData> generateFallbackCandles(String symbol) {
        List<CandleData> fallback = new ArrayList<>();
        MarketQuote quote = quoteCache.containsKey(symbol) ? quoteCache.get(symbol).quote() : null;
        double base = (quote != null && quote.price() > 0) ? quote.price() : getBaseFallbackPrice(symbol);
        long nowSec = System.currentTimeMillis() / 1000;
        int count = 60;
        long step = 300; // 5 min
        double cur = base;
        Random rng = new Random(symbol.hashCode() ^ (nowSec / 300));
        double maxDelta = base * 0.008;
        double maxSpread = base * 0.004;

        for (int k = count; k >= 0; k--) {
            long t = nowSec - (k * step);
            double delta = (rng.nextDouble() - 0.49) * maxDelta;
            double o = cur;
            double c = cur + delta;
            if (c <= 0) c = o * 0.99;
            double h = Math.max(o, c) + (rng.nextDouble() * maxSpread);
            double l = Math.max(Math.min(o, c) - (rng.nextDouble() * maxSpread), base * 0.2);
            long v = (long) (50000 + rng.nextDouble() * 500000);
            fallback.add(new CandleData(t, roundPrice(o), roundPrice(h), roundPrice(l), roundPrice(c), v));
            cur = c;
        }
        return fallback;
    }

    private long estimateMarketCap(String symbol, double price, long yahooMarketCap) {
        if (yahooMarketCap > 0) {
            return yahooMarketCap;
        }
        String sym = symbol.toUpperCase();
        if (sym.startsWith("BTC")) return (long) (19_750_000L * price);
        if (sym.startsWith("ETH")) return (long) (120_200_000L * price);
        if (sym.startsWith("SOL")) return (long) (466_000_000L * price);
        if (sym.startsWith("DOGE")) return (long) (145_000_000_000L * price);
        if (sym.startsWith("SHIB")) return (long) (589_000_000_000_000L * price);
        if (sym.startsWith("PEPE")) return (long) (420_690_000_000_000L * price);
        if (sym.startsWith("NVDA")) return (long) (24_500_000_000L * price);
        if (sym.startsWith("AAPL")) return (long) (15_300_000_000L * price);
        if (sym.startsWith("MSFT")) return (long) (7_430_000_000L * price);
        if (sym.startsWith("AMZN")) return (long) (10_400_000_000L * price);
        if (sym.startsWith("TSLA")) return (long) (3_180_000_000L * price);
        return (long) (100_000_000L * price);
    }

    private double getBaseFallbackPrice(String sym) {
        if (sym.startsWith("BTC")) return 62500.0;
        if (sym.startsWith("ETH")) return 2480.0;
        if (sym.startsWith("SOL")) return 142.0;
        if (sym.startsWith("DOGE")) return 0.091;
        if (sym.startsWith("SHIB")) return 0.000014;
        if (sym.startsWith("PEPE")) return 0.0000062;
        if (sym.startsWith("NVDA")) return 106.5;
        if (sym.startsWith("TSLA")) return 215.0;
        if (sym.startsWith("AAPL")) return 220.0;
        if (sym.startsWith("MSFT")) return 410.0;
        if (sym.startsWith("AMZN")) return 175.0;
        return 100.0;
    }

    private String normalizeSymbol(String sym) {
        if (sym == null) return "MSFT";
        String s = sym.trim().toUpperCase();
        if (s.equals("ETH") || s.equals("BTC") || s.equals("SOL") || s.equals("DOGE") || s.equals("SHIB") || s.equals("PEPE")) {
            return s + "-USD";
        }
        return s;
    }

    private double roundPrice(double val) {
        if (val < 0.0001) {
            // For sub-penny memecoins like PEPE (0.0000062) or SHIB (0.000014)
            return Math.round(val * 1_000_000_000.0) / 1_000_000_000.0;
        } else if (val < 0.01) {
            return Math.round(val * 100_000_000.0) / 100_000_000.0;
        } else if (val < 1.0) {
            return Math.round(val * 10_000.0) / 10_000.0;
        } else {
            return Math.round(val * 100.0) / 100.0;
        }
    }

    private double round(double val, int decimals) {
        double factor = Math.pow(10, decimals);
        return Math.round(val * factor) / factor;
    }
}
