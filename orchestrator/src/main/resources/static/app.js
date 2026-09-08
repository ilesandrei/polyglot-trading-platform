/**
 * Polyglot Trading Platform — Terminal Frontend Logic
 * Live Market & Crypto Paper-Trading Engine
 */

const API_BASE = '/api';

const USERS = {
  buyer: {
    id: '00000000-0000-0000-0000-000000000001',
    name: 'Trader 1 (Buyer)'
  },
  seller: {
    id: '00000000-0000-0000-0000-000000000002',
    name: 'Trader 2 (Seller)'
  }
};

let currentUserId = USERS.buyer.id;
let currentSymbol = 'ETH-USD';
let currentSide = 'BUY';
let currentMode = 'INSTANT'; // 'INSTANT' or 'LIMIT'
let currentCategoryFilter = 'ALL';
let userCash = 0;
let userHoldings = 0;
let latestQuote = null;
const quoteCache = new Map(); // symbol -> quote object

// ── DOM References ────────────────────────────────────────────────
const userSelect = document.getElementById('user-select');
const symbolBadge = document.getElementById('active-symbol-badge');
const cashDisplay = document.getElementById('metric-cash');
const holdingsDisplay = document.getElementById('metric-holdings');
const estValueDisplay = document.getElementById('metric-est-value');
const orderbookBids = document.getElementById('orderbook-bids');
const orderbookAsks = document.getElementById('orderbook-asks');
const spreadDisplay = document.getElementById('spread-value');
const positionsBody = document.getElementById('positions-body');
const ordersBody = document.getElementById('orders-body');
const tradesBody = document.getElementById('trades-body');
const orderForm = document.getElementById('order-form');
const inputPrice = document.getElementById('order-price');
const inputQty = document.getElementById('order-qty');
const orderTotalEstimate = document.getElementById('order-total-estimate');
const submitBtn = document.getElementById('submit-order-btn');
const toastContainer = document.getElementById('toast-container');
const catalogChipsRow = document.getElementById('catalog-chips-row');
const searchInput = document.getElementById('ticker-search-input');
const searchBtn = document.getElementById('ticker-search-btn');
const modeInstantBtn = document.getElementById('mode-instant-btn');
const modeLimitBtn = document.getElementById('mode-limit-btn');
const priceInputLabel = document.getElementById('price-input-label');
const priceHint = document.getElementById('price-hint');
const qtySymbolLabel = document.getElementById('qty-symbol-label');

// Hero Banner elements
const heroIcon = document.getElementById('hero-icon');
const heroSymbol = document.getElementById('hero-symbol');
const heroName = document.getElementById('hero-name');
const heroCategoryTag = document.getElementById('hero-category-tag');
const heroPrice = document.getElementById('hero-price');
const heroChange = document.getElementById('hero-change');
const heroHigh = document.getElementById('hero-high');
const heroLow = document.getElementById('hero-low');
const heroExecMode = document.getElementById('hero-exec-mode');

// Catalog state
let catalogAssets = [];

// ── Chart State ───────────────────────────────────────────────────
let chart = null;
let candleSeries = null;
let areaSeries = null;
let volumeSeries = null;
let smaSeries = null;
let currentChartType = 'CANDLES';
let verticalZoom = 1.0;
let isDraggingRightScale = false;
let dragStartY = 0;
let dragStartZoom = 1.0;
let showSma = true;
let showVolume = true;
let currentRange = '1d';
let currentInterval = '5m';
let currentCandles = [];
let memecoinScaleMode = 'MCAP'; // 'MCAP' (show market cap in dollars on side scale) or 'PRICE'

// ── Initialization ────────────────────────────────────────────────
document.addEventListener('DOMContentLoaded', async () => {
  initEventListeners();
  initChart();
  await loadCatalog();
  await selectAsset(currentSymbol);
  refreshAll();

  // Poll intervals:
  // - Live market quote for active symbol every 2.5s
  setInterval(() => fetchLiveQuote(currentSymbol), 2500);
  // - Engine order book, portfolio, and trades every 2.5s
  setInterval(refreshAll, 2500);
});

function initEventListeners() {
  // Active Trader selector
  userSelect.addEventListener('change', (e) => {
    currentUserId = e.target.value;
    refreshAll();
  });

  // Catalog category tabs filter
  document.querySelectorAll('.catalog-tab-btn').forEach((btn) => {
    btn.addEventListener('click', () => {
      document.querySelectorAll('.catalog-tab-btn').forEach((b) => b.classList.remove('active'));
      btn.classList.add('active');
      currentCategoryFilter = btn.dataset.cat;
      renderCatalogChips();
    });
  });

  // Ticker Search input + button
  searchBtn.addEventListener('click', handleSearch);
  searchInput.addEventListener('keydown', (e) => {
    if (e.key === 'Enter') {
      e.preventDefault();
      handleSearch();
    }
  });

  // Execution Mode Switcher (Instant Fill vs Limit Order)
  if (modeInstantBtn && modeLimitBtn) {
    modeInstantBtn.addEventListener('click', () => setExecutionMode('INSTANT'));
    modeLimitBtn.addEventListener('click', () => setExecutionMode('LIMIT'));
  }

  // Buy / Sell Side toggle
  document.querySelectorAll('.side-btn').forEach((btn) => {
    btn.addEventListener('click', () => {
      document.querySelectorAll('.side-btn').forEach((b) => {
        b.classList.remove('active-buy', 'active-sell');
      });
      currentSide = btn.dataset.side;
      if (currentSide === 'BUY') {
        btn.classList.add('active-buy');
        submitBtn.className = 'submit-order-btn submit-buy';
        submitBtn.textContent = `Buy ${currentSymbol}`;
      } else {
        btn.classList.add('active-sell');
        submitBtn.className = 'submit-order-btn submit-sell';
        submitBtn.textContent = `Sell ${currentSymbol}`;
      }
      updateEstimate();
    });
  });

  // Inputs live recalculation
  inputPrice.addEventListener('input', updateEstimate);
  inputQty.addEventListener('input', updateEstimate);

  // Quick percent buttons
  document.querySelectorAll('.pct-btn').forEach((btn) => {
    btn.addEventListener('click', () => {
      const pct = parseFloat(btn.dataset.pct);
      const price = parseFloat(inputPrice.value) || (latestQuote ? latestQuote.price : 100);
      if (currentSide === 'BUY') {
        if (price > 0 && userCash > 0) {
          const maxQty = (userCash * pct) / price;
          inputQty.value = formatQtyForInput(maxQty);
        }
      } else {
        if (userHoldings > 0) {
          inputQty.value = formatQtyForInput(userHoldings * pct);
        }
      }
      updateEstimate();
    });
  });

  // Submit Order Form
  orderForm.addEventListener('submit', handleOrderSubmit);
}

function formatQtyForInput(qty) {
  if (qty >= 100) return Math.floor(qty).toString();
  if (qty >= 1) return (Math.floor(qty * 100) / 100).toString();
  return (Math.floor(qty * 10000) / 10000).toString();
}

function setExecutionMode(mode) {
  currentMode = mode;
  if (mode === 'INSTANT') {
    modeInstantBtn.classList.add('active');
    modeLimitBtn.classList.remove('active');
    priceInputLabel.textContent = 'Instant Market Price';
    priceHint.style.display = 'block';
    inputPrice.readOnly = true;
    inputPrice.style.background = 'rgba(15, 23, 42, 0.4)';
    heroExecMode.textContent = 'Instant Paper';
    if (latestQuote) {
      inputPrice.value = latestQuote.price;
      updateEstimate();
    }
  } else {
    modeLimitBtn.classList.add('active');
    modeInstantBtn.classList.remove('active');
    priceInputLabel.textContent = 'Limit Price';
    priceHint.style.display = 'none';
    inputPrice.readOnly = false;
    inputPrice.style.background = 'rgba(15, 23, 42, 0.8)';
    heroExecMode.textContent = 'C++ Limit Book';
  }
}

// ── Asset Catalog & Search ─────────────────────────────────────────
async function loadCatalog() {
  try {
    const res = await fetch(`${API_BASE}/market/catalog`);
    if (res.ok) {
      catalogAssets = await res.json();
      renderCatalogChips();
    }
  } catch (err) {
    console.error('Failed to load asset catalog:', err);
  }
}

function renderCatalogChips() {
  if (!catalogChipsRow) return;
  catalogChipsRow.innerHTML = '';

  const filtered = catalogAssets.filter((a) => {
    if (currentCategoryFilter === 'ALL') return true;
    return a.category === currentCategoryFilter;
  });

  filtered.forEach((asset) => {
    const chip = document.createElement('div');
    chip.className = `asset-chip ${asset.symbol === currentSymbol ? 'active' : ''}`;
    chip.id = `chip-${asset.symbol}`;
    chip.innerHTML = `
      <span>${asset.icon || '🪙'}</span>
      <span class="asset-chip-symbol">${asset.symbol}</span>
      <span class="asset-chip-price" id="chip-price-${asset.symbol}">$${asset.livePrice ? formatPrice(asset.livePrice) : '...'}</span>
    `;
    chip.onclick = () => selectAsset(asset.symbol);
    catalogChipsRow.appendChild(chip);
  });
}

async function selectAsset(symbol) {
  currentSymbol = symbol;
  currentCandles = []; // Clear candles immediately to prevent cross-symbol scale conflicts
  verticalZoom = 1.0; // Reset vertical height zoom to clean default on symbol switch
  if (symbolBadge) symbolBadge.textContent = symbol;
  if (qtySymbolLabel) qtySymbolLabel.textContent = symbol;
  submitBtn.textContent = `${currentSide === 'BUY' ? 'Buy' : 'Sell'} ${currentSymbol}`;

  // Highlight active chip
  document.querySelectorAll('.asset-chip').forEach((c) => c.classList.remove('active'));
  const activeChip = document.getElementById(`chip-${symbol}`);
  if (activeChip) activeChip.classList.add('active');

  // Show or hide the memecoin scale toggle based on asset type
  const memecoinToggle = document.getElementById('memecoin-scale-group');
  if (memecoinToggle) {
    memecoinToggle.style.display = isMemecoin(symbol) ? 'flex' : 'none';
  }

  // Immediately fetch live quote and chart candles
  await Promise.all([
    fetchLiveQuote(symbol),
    loadChartData(symbol)
  ]);
  refreshAll();
}

async function handleSearch() {
  const query = searchInput.value.trim().toUpperCase();
  if (!query) return;

  searchBtn.textContent = 'Searching...';
  searchBtn.disabled = true;

  try {
    const quote = await fetchLiveQuote(query);
    if (quote && quote.symbol) {
      currentSymbol = quote.symbol;

      // Add to catalog if not present
      if (!catalogAssets.some((a) => a.symbol === quote.symbol)) {
        catalogAssets.unshift({
          symbol: quote.symbol,
          name: quote.name || quote.symbol,
          category: quote.category || 'CRYPTO',
          icon: quote.icon || '🪙',
          livePrice: quote.price
        });
        renderCatalogChips();
      }
      await selectAsset(quote.symbol);
      showToast(`Selected ${quote.symbol} @ $${formatPrice(quote.price)}`, 'success');
      searchInput.value = '';
    } else {
      showToast(`Could not find live quote for "${query}"`, 'error');
    }
  } catch (err) {
    showToast(`Search failed: ${err.message}`, 'error');
  } finally {
    searchBtn.textContent = 'Quote & Trade';
    searchBtn.disabled = false;
  }
}

// ── Live Quote Fetching ────────────────────────────────────────────
async function fetchLiveQuote(symbol) {
  try {
    const res = await fetch(`${API_BASE}/market/quote?symbol=${encodeURIComponent(symbol)}`);
    if (!res.ok) return null;
    const quote = await res.json();

    latestQuote = quote;
    quoteCache.set(quote.symbol, quote);

    // Update Hero Banner
    if (heroSymbol) heroSymbol.textContent = quote.symbol;
    if (heroName) heroName.textContent = `${quote.name || quote.symbol} • ${quote.category === 'CRYPTO' ? '24/7 Crypto Market' : 'US Stock Market'}`;
    if (heroIcon) heroIcon.textContent = quote.icon || (quote.category === 'CRYPTO' ? '🪙' : '📈');
    if (heroCategoryTag) heroCategoryTag.textContent = quote.category;
    if (heroPrice) heroPrice.textContent = `$${formatPrice(quote.price)}`;

    if (heroChange) {
      const chg = (quote.changePercent !== undefined && quote.changePercent !== null) ? quote.changePercent : (quote.change24h || 0);
      const isUp = chg >= 0;
      heroChange.className = `ticker-change-badge ${isUp ? 'badge-up' : 'badge-down'}`;
      heroChange.textContent = `${isUp ? '+' : ''}${Number(chg).toFixed(2)}%`;
    }

    if (heroHigh) heroHigh.textContent = `$${formatPrice(quote.dayHigh || quote.high24h || quote.price)}`;
    if (heroLow) heroLow.textContent = `$${formatPrice(quote.dayLow || quote.low24h || quote.price)}`;

    const heroMcap = document.getElementById('hero-mcap');
    if (heroMcap) {
      heroMcap.textContent = formatMcap(quote.marketCap);
    }

    // Update chip price in catalog bar if visible
    const chipPriceEl = document.getElementById(`chip-price-${quote.symbol}`);
    if (chipPriceEl) {
      chipPriceEl.textContent = `$${formatPrice(quote.price)}`;
    }

    // Live chart candle ticking
    if (quote.symbol === currentSymbol) {
      tickActiveCandle(quote.price);
    }

    // If in instant mode, lock price to live quote
    if (currentMode === 'INSTANT') {
      inputPrice.value = quote.price;
      updateEstimate();
    }

    return quote;
  } catch (err) {
    console.error(`Error fetching quote for ${symbol}:`, err);
    return null;
  }
}

function formatPrice(val) {
  if (val === undefined || val === null) return '0.00';
  const num = Number(val);
  if (num >= 100) return num.toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
  if (num >= 1) return num.toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 4 });
  if (num >= 0.001) return num.toFixed(6);
  return num.toFixed(8);
}

function formatMcap(val) {
  if (!val || val <= 0) return '—';
  const num = Number(val);
  if (num >= 1_000_000_000_000) return `$${(num / 1_000_000_000_000).toFixed(2)}T`;
  if (num >= 1_000_000_000) return `$${(num / 1_000_000_000).toFixed(2)}B`;
  if (num >= 1_000_000) return `$${(num / 1_000_000).toFixed(2)}M`;
  return `$${num.toLocaleString('en-US')}`;
}

function isMemecoin(symbol) {
  const s = (symbol || '').toUpperCase();
  return s.startsWith('PEPE') || s.startsWith('SHIB') || s.startsWith('DOGE') || s.includes('MEME');
}

function getCirculatingSupply(symbol) {
  const s = (symbol || '').toUpperCase();
  if (s.startsWith('PEPE')) return 420_690_000_000_000;
  if (s.startsWith('SHIB')) return 589_000_000_000_000;
  if (s.startsWith('DOGE')) return 145_000_000_000;
  if (latestQuote && latestQuote.marketCap && latestQuote.price > 0) {
    return latestQuote.marketCap / latestQuote.price;
  }
  return null;
}

function formatMcapAxis(val) {
  if (val === undefined || val === null || isNaN(val) || val <= 0) return '$0.00';
  const num = Number(val);
  if (num >= 1_000_000_000_000) {
    return `$${(num / 1_000_000_000_000).toFixed(2)}T`;
  }
  if (num >= 1_000_000_000) {
    const b = num / 1_000_000_000;
    const decimals = verticalZoom > 1.2 ? 3 : 2;
    return `$${b.toFixed(decimals)}B`;
  }
  if (num >= 1_000_000) {
    const m = num / 1_000_000;
    const decimals = verticalZoom > 1.2 ? 3 : 2;
    return `$${m.toFixed(decimals)}M`;
  }
  if (num >= 1_000) {
    return `$${(num / 1_000).toFixed(1)}K`;
  }
  return `$${num.toFixed(2)}`;
}

function getPriceFormatForSymbol(symbol, samplePrice) {
  const p = samplePrice || (latestQuote && latestQuote.symbol === symbol ? latestQuote.price : 100);

  // If this is a memecoin and side axis mode is set to MCAP (default), format the right scale as Market Cap in dollars!
  if (isMemecoin(symbol) && memecoinScaleMode === 'MCAP') {
    const supply = getCirculatingSupply(symbol) || 420_690_000_000_000;
    const minMove = p < 0.0001 ? 0.00000001 : (p < 0.01 ? 0.000001 : 0.0001);
    return {
      type: 'custom',
      minMove: minMove,
      formatter: (price) => formatMcapAxis(price * supply)
    };
  }

  if (p < 0.0001) {
    return {
      type: 'price',
      precision: 8,
      minMove: 0.00000001,
    };
  } else if (p < 0.01) {
    return {
      type: 'price',
      precision: 6,
      minMove: 0.000001,
    };
  } else if (p < 1.0) {
    return {
      type: 'price',
      precision: 4,
      minMove: 0.0001,
    };
  } else {
    return {
      type: 'price',
      precision: 2,
      minMove: 0.01,
    };
  }
}

function updateEstimate() {
  const p = parseFloat(inputPrice.value) || 0;
  const q = parseFloat(inputQty.value) || 0;
  const total = p * q;
  orderTotalEstimate.textContent = `$${total.toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;
}

// ── Master Refresh ────────────────────────────────────────────────
async function refreshAll() {
  await Promise.all([
    fetchPortfolio(),
    fetchOrdersAndOrderBook(),
    fetchTrades()
  ]);
}

// ── Portfolio & Positions with Live PnL ────────────────────────────
async function fetchPortfolio() {
  try {
    const res = await fetch(`${API_BASE}/portfolio/${currentUserId}`);
    if (!res.ok) return;
    const data = await res.json();

    userCash = parseFloat(data.cash) || 0;
    cashDisplay.textContent = `$${userCash.toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;

    positionsBody.innerHTML = '';
    let totalStockVal = 0;
    userHoldings = 0;

    // Filter out zero-quantity or closed positions
    const activePositions = (data.positions || []).filter((pos) => {
      const q = parseFloat(pos.quantity) || 0;
      return q > 0.00000001;
    });

    if (activePositions.length > 0) {
      for (const pos of activePositions) {
        const qty = parseFloat(pos.quantity) || 0;
        const avgCost = parseFloat(pos.averageCost) || 0;

        // Fetch or get cached live quote for this position
        let livePrice = avgCost;
        if (quoteCache.has(pos.symbol)) {
          livePrice = quoteCache.get(pos.symbol).price;
        } else if (pos.symbol === currentSymbol && latestQuote) {
          livePrice = latestQuote.price;
        } else {
          // Asynchronously fetch quote in background and cache
          fetchLiveQuoteSilently(pos.symbol);
        }

        const posValue = qty * livePrice;
        const costBasis = qty * avgCost;
        const pnl = posValue - costBasis;
        const pnlPct = costBasis > 0 ? (pnl / costBasis) * 100 : 0;
        totalStockVal += posValue;

        if (pos.symbol === currentSymbol) {
          userHoldings = qty;
        }

        const isProfit = pnl >= 0;
        const pnlBadgeClass = isProfit ? 'pnl-badge pnl-profit' : 'pnl-badge pnl-loss';
        const pnlSign = isProfit ? '+' : '';

        const tr = document.createElement('tr');
        tr.innerHTML = `
          <td><strong style="cursor: pointer; color: var(--accent-cyan);" onclick="selectAsset('${pos.symbol}')">${pos.symbol}</strong></td>
          <td style="font-family: var(--font-mono);">${qty >= 1 ? qty.toFixed(4) : qty.toFixed(6)}</td>
          <td style="font-family: var(--font-mono);">$${formatPrice(avgCost)}</td>
          <td style="font-family: var(--font-mono); color: var(--text-primary);">$${formatPrice(livePrice)}</td>
          <td style="font-family: var(--font-mono);">$${formatPrice(posValue)}</td>
          <td>
            <span class="${pnlBadgeClass}">
              ${pnlSign}$${pnl.toFixed(2)} (${pnlSign}${pnlPct.toFixed(1)}%)
            </span>
          </td>
          <td>
            <button class="close-pos-btn" onclick="closePosition('${pos.symbol}', ${qty})">Sell</button>
          </td>
        `;
        positionsBody.appendChild(tr);
      }
    } else {
      positionsBody.innerHTML = '<tr><td colspan="7" style="text-align: center; color: var(--text-muted);">No open positions. Use Instant Market Fill to buy assets.</td></tr>';
    }

    holdingsDisplay.textContent = `${userHoldings.toFixed(4)} ${currentSymbol}`;
    const equity = userCash + totalStockVal;
    estValueDisplay.textContent = `$${equity.toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;
  } catch (err) {
    console.error('Error loading portfolio:', err);
  }
}

async function fetchLiveQuoteSilently(symbol) {
  try {
    const res = await fetch(`${API_BASE}/market/quote?symbol=${encodeURIComponent(symbol)}`);
    if (res.ok) {
      const q = await res.json();
      quoteCache.set(q.symbol, q);
    }
  } catch (_) {}
}

// 1-Click Sell / Close Position
window.closePosition = async function(symbol, quantity) {
  // Switch to that asset, set to SELL, instant market mode
  await selectAsset(symbol);
  document.querySelectorAll('.side-btn').forEach((b) => b.classList.remove('active-buy', 'active-sell'));
  const sellBtn = document.querySelector('.side-btn[data-side="SELL"]');
  if (sellBtn) sellBtn.classList.add('active-sell');
  currentSide = 'SELL';
  setExecutionMode('INSTANT');

  inputQty.value = quantity;
  submitBtn.className = 'submit-order-btn submit-sell';
  submitBtn.textContent = `Sell ${symbol}`;
  updateEstimate();

  showToast(`Prepared 1-click Sell for ${quantity} ${symbol} at Live Price. Click 'Sell ${symbol}' to confirm.`, 'success');
};

// ── Orders & Depth ────────────────────────────────────────────────
async function fetchOrdersAndOrderBook() {
  try {
    const [buyerRes, sellerRes] = await Promise.all([
      fetch(`${API_BASE}/orders?userId=${USERS.buyer.id}`),
      fetch(`${API_BASE}/orders?userId=${USERS.seller.id}`)
    ]);

    const buyerOrders = buyerRes.ok ? await buyerRes.json() : [];
    const sellerOrders = sellerRes.ok ? await sellerRes.json() : [];
    const allOrders = [...buyerOrders, ...sellerOrders];

    // 1. Render Active User Orders Table
    const userOrders = currentUserId === USERS.buyer.id ? buyerOrders : sellerOrders;
    renderUserOrdersTable(userOrders);

    // 2. Build Order Book Depth
    renderOrderBook(allOrders.filter((o) => o.symbol === currentSymbol));
  } catch (err) {
    console.error('Error loading orders:', err);
  }
}

function renderUserOrdersTable(orders) {
  ordersBody.innerHTML = '';
  if (!orders || orders.length === 0) {
    ordersBody.innerHTML = '<tr><td colspan="7" style="text-align: center; color: var(--text-muted);">No orders found</td></tr>';
    return;
  }

  orders.slice(0, 10).forEach((order) => {
    const tr = document.createElement('tr');
    const isBuy = order.side === 'BUY';
    const tagClass = isBuy ? 'tag-buy' : 'tag-sell';
    const statusClass = order.status === 'FILLED' ? 'tag-filled' : (order.status === 'PENDING' ? 'tag-pending' : '');

    const canCancel = order.status === 'PENDING' || order.status === 'VALIDATED';
    const actionHtml = canCancel
      ? `<button class="cancel-action-btn" onclick="cancelOrder('${order.id}')">Cancel</button>`
      : `<span style="color: var(--text-muted); font-size: 0.75rem;">—</span>`;

    tr.innerHTML = `
      <td><span class="badge-tag ${tagClass}">${order.side}</span></td>
      <td><strong>${order.symbol}</strong></td>
      <td style="font-family: var(--font-mono);">${parseFloat(order.quantity).toFixed(4)}</td>
      <td style="font-family: var(--font-mono);">$${formatPrice(order.price)}</td>
      <td><span class="badge-tag ${statusClass}">${order.status}</span></td>
      <td style="font-size: 0.75rem; color: var(--text-secondary);">${new Date(order.createdAt).toLocaleTimeString()}</td>
      <td>${actionHtml}</td>
    `;
    ordersBody.appendChild(tr);
  });
}

function renderOrderBook(orders) {
  const bids = [];
  const asks = [];

  orders.forEach((o) => {
    if (o.status === 'PENDING' || o.status === 'VALIDATED') {
      const price = parseFloat(o.price);
      const qty = parseFloat(o.quantity);
      if (o.side === 'BUY') {
        bids.push({ price, qty });
      } else {
        asks.push({ price, qty });
      }
    }
  });

  bids.sort((a, b) => b.price - a.price);
  asks.sort((a, b) => a.price - b.price);

  orderbookAsks.innerHTML = '';
  const topAsks = asks.slice(0, 5).reverse();
  const maxAskQty = Math.max(...topAsks.map((a) => a.qty), 10);

  topAsks.forEach((a) => {
    const pct = Math.min(100, (a.qty / maxAskQty) * 100);
    const row = document.createElement('tr');
    row.className = 'orderbook-row';
    row.onclick = () => { inputPrice.value = a.price; updateEstimate(); };
    row.innerHTML = `
      <td class="ask-price">$${formatPrice(a.price)}</td>
      <td style="font-family: var(--font-mono);">${a.qty.toFixed(2)}</td>
      <td style="font-family: var(--font-mono);">$${formatPrice(a.price * a.qty)}
        <div class="orderbook-depth-bar ask-depth" style="width: ${pct}%;"></div>
      </td>
    `;
    orderbookAsks.appendChild(row);
  });

  if (bids.length > 0 && asks.length > 0) {
    const spread = (asks[0].price - bids[0].price).toFixed(2);
    spreadDisplay.textContent = `Spread: $${spread}`;
  } else {
    spreadDisplay.textContent = 'Spread: — (Real-time Live Market)';
  }

  orderbookBids.innerHTML = '';
  const topBids = bids.slice(0, 5);
  const maxBidQty = Math.max(...topBids.map((b) => b.qty), 10);

  topBids.forEach((b) => {
    const pct = Math.min(100, (b.qty / maxBidQty) * 100);
    const row = document.createElement('tr');
    row.className = 'orderbook-row';
    row.onclick = () => { inputPrice.value = b.price; updateEstimate(); };
    row.innerHTML = `
      <td class="bid-price">$${formatPrice(b.price)}</td>
      <td style="font-family: var(--font-mono);">${b.qty.toFixed(2)}</td>
      <td style="font-family: var(--font-mono);">$${formatPrice(b.price * b.qty)}
        <div class="orderbook-depth-bar bid-depth" style="width: ${pct}%;"></div>
      </td>
    `;
    orderbookBids.appendChild(row);
  });
}

// ── Trades Feed ───────────────────────────────────────────────────
async function fetchTrades() {
  try {
    const res = await fetch(`${API_BASE}/trades?symbol=${encodeURIComponent(currentSymbol)}`);
    if (!res.ok) return;
    const trades = await res.json();

    tradesBody.innerHTML = '';
    if (!trades || trades.length === 0) {
      tradesBody.innerHTML = '<tr><td colspan="4" style="text-align: center; color: var(--text-muted);">No executed trades yet</td></tr>';
      return;
    }

    trades.slice(0, 8).forEach((trade) => {
      const tr = document.createElement('tr');
      tr.innerHTML = `
        <td style="color: var(--buy-green); font-weight: 600; font-family: var(--font-mono);">$${formatPrice(trade.price)}</td>
        <td style="font-family: var(--font-mono);">${parseFloat(trade.quantity).toFixed(4)}</td>
        <td style="color: var(--text-secondary); font-family: var(--font-mono);">$${formatPrice(parseFloat(trade.price) * parseFloat(trade.quantity))}</td>
        <td style="font-size: 0.72rem; color: var(--text-muted);">${new Date(trade.executedAt).toLocaleTimeString()}</td>
      `;
      tradesBody.appendChild(tr);
    });
  } catch (err) {
    console.error('Error loading trades:', err);
  }
}

// ── Order Placement Action ─────────────────────────────────────────
async function handleOrderSubmit(e) {
  e.preventDefault();

  const price = parseFloat(inputPrice.value);
  const qty = parseFloat(inputQty.value);

  if (!price || price <= 0 || !qty || qty <= 0) {
    showToast('Please enter a valid price and quantity.', 'error');
    return;
  }

  submitBtn.disabled = true;
  submitBtn.textContent = 'Executing...';

  try {
    if (currentMode === 'INSTANT') {
      // ⚡ Instant Paper Market Execution against real-world live quote
      const payload = {
        userId: currentUserId,
        symbol: currentSymbol,
        side: currentSide,
        quantity: qty,
        price: price
      };

      const res = await fetch(`${API_BASE}/market/instant-order`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload)
      });

      const data = await res.json();

      if (res.ok && data.status === 'FILLED') {
        const filledAt = data.price !== undefined ? data.price : data.fillPrice;
        showToast(`⚡ Instant Fill: ${currentSide} ${qty} ${currentSymbol} @ $${formatPrice(filledAt)}`, 'success');
        await refreshAll();
      } else {
        showToast(`Instant Trade Rejected: ${data.message || 'Risk check failed'}`, 'error');
      }
    } else {
      // 📖 Limit Order routed to C++ matching engine
      const payload = {
        userId: currentUserId,
        symbol: currentSymbol,
        side: currentSide,
        type: 'LIMIT',
        quantity: qty,
        price: price
      };

      const res = await fetch(`${API_BASE}/orders`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload)
      });

      const data = await res.json();

      if (res.ok) {
        showToast(`Limit Order Placed: ${currentSide} ${qty} ${currentSymbol} @ $${formatPrice(price)} (${data.status})`, 'success');
        await refreshAll();
      } else {
        showToast(`Order Rejected: ${data.message || 'Risk check failed'}`, 'error');
      }
    }
  } catch (err) {
    showToast(`Order execution error: ${err.message}`, 'error');
  } finally {
    submitBtn.disabled = false;
    submitBtn.textContent = `${currentSide === 'BUY' ? 'Buy' : 'Sell'} ${currentSymbol}`;
  }
}

// ── Order Cancellation Action ─────────────────────────────────────
window.cancelOrder = async function(orderId) {
  try {
    const res = await fetch(`${API_BASE}/orders/${orderId}`, {
      method: 'DELETE'
    });

    if (res.ok) {
      showToast('Order cancelled successfully', 'success');
      refreshAll();
    } else {
      const msg = await res.text();
      showToast(`Cancel failed: ${msg}`, 'error');
    }
  } catch (err) {
    showToast(`Error: ${err.message}`, 'error');
  }
};

// ── Toast Utility ─────────────────────────────────────────────────
function showToast(message, type = 'success') {
  const toast = document.createElement('div');
  toast.className = `toast toast-${type}`;
  toast.innerHTML = `
    <span>${type === 'success' ? '✓' : '⚠'}</span>
    <div>${message}</div>
  `;
  toastContainer.appendChild(toast);

  setTimeout(() => {
    toast.style.opacity = '0';
    toast.style.transform = 'translateY(10px)';
    toast.style.transition = 'all 0.3s ease';
    setTimeout(() => toast.remove(), 300);
  }, 4000);
}

// ── Interactive TradingView Lightweight Chart Engine ──────────────
function initChart() {
  const container = document.getElementById('chart-container');
  if (!container || typeof LightweightCharts === 'undefined') return;

  try {
    chart = LightweightCharts.createChart(container, {
      width: container.clientWidth || 800,
      height: 380,
      layout: {
        background: { type: 'solid', color: '#0f172a' },
        textColor: '#94a3b8',
        fontFamily: "'Inter', sans-serif"
      },
      grid: {
        vertLines: { color: 'rgba(255, 255, 255, 0.04)' },
        horzLines: { color: 'rgba(255, 255, 255, 0.04)' }
      },
      crosshair: {
        mode: LightweightCharts.CrosshairMode.Normal,
        vertLine: { color: 'rgba(0, 242, 254, 0.4)', width: 1, style: 3 },
        horzLine: { color: 'rgba(0, 242, 254, 0.4)', width: 1, style: 3 }
      },
      rightPriceScale: {
        borderColor: 'rgba(255, 255, 255, 0.1)',
        autoScale: true
      },
      timeScale: {
        borderColor: 'rgba(255, 255, 255, 0.1)',
        timeVisible: true,
        secondsVisible: false
      }
    });

    // 1. Candlestick Series
    candleSeries = chart.addCandlestickSeries({
      upColor: '#10b981',
      downColor: '#f43f5e',
      borderUpColor: '#10b981',
      borderDownColor: '#f43f5e',
      wickUpColor: '#10b981',
      wickDownColor: '#f43f5e'
    });

    // 2. Area Series (Line Mode)
    areaSeries = chart.addAreaSeries({
      topColor: 'rgba(0, 242, 254, 0.28)',
      bottomColor: 'rgba(0, 242, 254, 0.01)',
      lineColor: '#00f2fe',
      lineWidth: 2,
      visible: false
    });

    // 3. Volume Histogram Series
    volumeSeries = chart.addHistogramSeries({
      color: 'rgba(16, 185, 129, 0.35)',
      priceFormat: { type: 'volume' },
      priceScaleId: 'volume_scale'
    });
    chart.priceScale('volume_scale').applyOptions({
      scaleMargins: { top: 0.82, bottom: 0 }
    });

    // 4. SMA 20 Line Series
    smaSeries = chart.addLineSeries({
      color: '#f59e0b',
      lineWidth: 2,
      priceScaleId: 'right'
    });

    // Crosshair hover listener for legend
    chart.subscribeCrosshairMove(handleCrosshairMove);

    // Auto-resize on window resize
    window.addEventListener('resize', () => {
      if (chart && container) {
        chart.applyOptions({ width: container.clientWidth });
      }
    });

    // ── Right Price Scale Vertical Zoom (Locking Time Axis) ──
    // Mouse wheel over the right scale (~85px) stretches or compresses candle heights!
    container.addEventListener('wheel', (e) => {
      const rect = container.getBoundingClientRect();
      const mouseX = e.clientX - rect.left;
      const isRightScale = mouseX >= (rect.width - 85);

      if (isRightScale) {
        // Crucial: stop Lightweight Charts default horizontal time-scale zoom!
        e.preventDefault();
        e.stopPropagation();

        // Scroll UP (deltaY < 0) zooms in height (taller candles)
        // Scroll DOWN (deltaY > 0) zooms out height (shorter candles)
        const factor = e.deltaY < 0 ? 0.88 : 1.14;
        verticalZoom = Math.min(Math.max(verticalZoom * factor, 0.005), 80.0);
        applyVerticalScale();
      }
    }, { passive: false });

    // Dragging vertically on the right scale
    container.addEventListener('mousedown', (e) => {
      const rect = container.getBoundingClientRect();
      const mouseX = e.clientX - rect.left;
      if (mouseX >= rect.width - 85) {
        isDraggingRightScale = true;
        dragStartY = e.clientY;
        dragStartZoom = verticalZoom;
        document.body.style.cursor = 'ns-resize';
      }
    });

    window.addEventListener('mousemove', (e) => {
      if (isDraggingRightScale) {
        e.preventDefault();
        const dy = e.clientY - dragStartY;
        const factor = Math.exp(dy * 0.008);
        verticalZoom = Math.min(Math.max(dragStartZoom * factor, 0.005), 80.0);
        applyVerticalScale();
      } else if (container) {
        const rect = container.getBoundingClientRect();
        if (e.clientX >= rect.left && e.clientX <= rect.right && e.clientY >= rect.top && e.clientY <= rect.bottom) {
          const mouseX = e.clientX - rect.left;
          container.style.cursor = mouseX >= (rect.width - 85) ? 'ns-resize' : 'crosshair';
        }
      }
    });

    window.addEventListener('mouseup', () => {
      if (isDraggingRightScale) {
        isDraggingRightScale = false;
        document.body.style.cursor = 'default';
      }
    });

    // Double-click on right price scale resets height to Auto
    container.addEventListener('dblclick', (e) => {
      const rect = container.getBoundingClientRect();
      const mouseX = e.clientX - rect.left;
      if (mouseX >= rect.width - 85) {
        resetVerticalScale();
      }
    });

    initChartToolbar();
  } catch (err) {
    console.error('Failed to initialize chart:', err);
  }
}

function getAutoscaleInfoProvider() {
  if (Math.abs(verticalZoom - 1.0) < 0.001) {
    return null; // Return null so Lightweight Charts uses its native, high-performance autoscale
  }
  return (original) => {
    const res = original();
    if (!res || !res.priceRange) return res;

    const min = res.priceRange.minValue;
    const max = res.priceRange.maxValue;
    const mid = (max + min) / 2;
    const span = Math.max((max - min) * verticalZoom, 0.00000000001);

    return {
      priceRange: {
        minValue: mid - span / 2,
        maxValue: mid + span / 2
      },
      margins: res.margins
    };
  };
}

function applyVerticalScale() {
  const provider = getAutoscaleInfoProvider();
  if (candleSeries) candleSeries.applyOptions({ autoscaleInfoProvider: provider });
  if (areaSeries) areaSeries.applyOptions({ autoscaleInfoProvider: provider });
  if (Math.abs(verticalZoom - 1.0) < 0.001 && chart) {
    chart.priceScale('right').applyOptions({ autoScale: true });
  }
}

function resetVerticalScale() {
  verticalZoom = 1.0;
  applyVerticalScale();
  if (chart) {
    chart.priceScale('right').applyOptions({ autoScale: true });
    chart.timeScale().fitContent();
  }
}

function initChartToolbar() {
  // Timeframe buttons
  document.querySelectorAll('.tf-btn').forEach((btn) => {
    btn.addEventListener('click', () => {
      document.querySelectorAll('.tf-btn').forEach((b) => b.classList.remove('active'));
      btn.classList.add('active');
      const range = btn.dataset.range;
      const interval = btn.dataset.interval;
      loadChartData(currentSymbol, range, interval);
    });
  });

  // Chart type switcher (Candles vs Line)
  const btnCandles = document.getElementById('btn-chart-candles');
  const btnArea = document.getElementById('btn-chart-area');

  if (btnCandles && btnArea) {
    btnCandles.addEventListener('click', () => {
      btnCandles.classList.add('active');
      btnArea.classList.remove('active');
      currentChartType = 'CANDLES';
      if (candleSeries) candleSeries.applyOptions({ visible: true });
      if (areaSeries) areaSeries.applyOptions({ visible: false });
    });

    btnArea.addEventListener('click', () => {
      btnArea.classList.add('active');
      btnCandles.classList.remove('active');
      currentChartType = 'AREA';
      if (candleSeries) candleSeries.applyOptions({ visible: false });
      if (areaSeries) areaSeries.applyOptions({ visible: true });
    });
  }

  // Indicator toggles
  const btnSma = document.getElementById('btn-toggle-sma');
  const btnVol = document.getElementById('btn-toggle-vol');

  if (btnSma) {
    btnSma.addEventListener('click', () => {
      showSma = !showSma;
      btnSma.classList.toggle('active', showSma);
      if (smaSeries) smaSeries.applyOptions({ visible: showSma });
    });
  }

  if (btnVol) {
    btnVol.addEventListener('click', () => {
      showVolume = !showVolume;
      btnVol.classList.toggle('active', showVolume);
      if (volumeSeries) volumeSeries.applyOptions({ visible: showVolume });
    });
  }

  // Vertical Height Zoom Buttons
  const btnVZoomIn = document.getElementById('btn-vzoom-in');
  const btnVZoomOut = document.getElementById('btn-vzoom-out');
  const btnVZoomReset = document.getElementById('btn-vzoom-reset');

  if (btnVZoomIn) {
    btnVZoomIn.addEventListener('click', () => {
      verticalZoom = Math.max(verticalZoom * 0.8, 0.005);
      applyVerticalScale();
    });
  }

  if (btnVZoomOut) {
    btnVZoomOut.addEventListener('click', () => {
      verticalZoom = Math.min(verticalZoom * 1.25, 80.0);
      applyVerticalScale();
    });
  }

  if (btnVZoomReset) {
    btnVZoomReset.addEventListener('click', () => {
      resetVerticalScale();
      if (chart) chart.timeScale().fitContent();
    });
  }

  // Memecoin Side Scale Unit Switcher
  const btnSideMcap = document.getElementById('btn-side-mcap');
  const btnSidePrice = document.getElementById('btn-side-price');

  if (btnSideMcap && btnSidePrice) {
    btnSideMcap.addEventListener('click', () => {
      memecoinScaleMode = 'MCAP';
      btnSideMcap.classList.add('active');
      btnSidePrice.classList.remove('active');
      updateChartPriceFormat();
    });

    btnSidePrice.addEventListener('click', () => {
      memecoinScaleMode = 'PRICE';
      btnSidePrice.classList.add('active');
      btnSideMcap.classList.remove('active');
      updateChartPriceFormat();
    });
  }
}

function updateChartPriceFormat() {
  const pFormat = getPriceFormatForSymbol(currentSymbol, currentCandles[0]?.close);
  if (candleSeries) candleSeries.applyOptions({ priceFormat: pFormat });
  if (areaSeries) areaSeries.applyOptions({ priceFormat: pFormat });
  if (smaSeries) smaSeries.applyOptions({ priceFormat: pFormat });
  if (chart) {
    chart.priceScale('right').applyOptions({ autoScale: true });
  }
}

async function loadChartData(symbol, range = currentRange, interval = currentInterval) {
  currentRange = range;
  currentInterval = interval;
  const badge = document.getElementById('chart-symbol-badge');
  if (badge) badge.textContent = symbol;

  try {
    const res = await fetch(`${API_BASE}/market/history?symbol=${encodeURIComponent(symbol)}&range=${range}&interval=${interval}`);
    if (!res.ok) return;
    const data = await res.json();
    if (!data.candles || data.candles.length === 0) return;

    // Sort ascending by time
    const sorted = [...data.candles].sort((a, b) => a.time - b.time);
    currentCandles = sorted;

    // Apply precision and minMove based on asset price scale (e.g. 8 decimals for PEPE)
    const pFormat = getPriceFormatForSymbol(symbol, sorted[0]?.close);
    const provider = getAutoscaleInfoProvider();

    if (candleSeries) {
      candleSeries.applyOptions({ priceFormat: pFormat, autoscaleInfoProvider: provider });
      candleSeries.setData(sorted.map((c) => ({
        time: c.time,
        open: c.open,
        high: c.high,
        low: c.low,
        close: c.close
      })));
    }

    if (areaSeries) {
      areaSeries.applyOptions({ priceFormat: pFormat, autoscaleInfoProvider: provider });
      areaSeries.setData(sorted.map((c) => ({
        time: c.time,
        value: c.close
      })));
    }

    if (volumeSeries) {
      volumeSeries.setData(sorted.map((c) => ({
        time: c.time,
        value: c.volume,
        color: c.close >= c.open ? 'rgba(16, 185, 129, 0.45)' : 'rgba(244, 63, 94, 0.45)'
      })));
    }

    if (smaSeries) {
      smaSeries.applyOptions({ priceFormat: pFormat });
      const smaData = calculateSMA(sorted, 20);
      smaSeries.setData(smaData);
    }

    if (chart) {
      chart.priceScale('right').applyOptions({ autoScale: true });
      chart.timeScale().fitContent();
    }

    if (sorted.length > 0) {
      updateLegend(sorted[sorted.length - 1]);
    }
  } catch (err) {
    console.error('Error loading chart candles:', err);
  }
}

function calculateSMA(data, period = 20) {
  const result = [];
  if (data.length < period) return result;
  for (let i = period - 1; i < data.length; i++) {
    let sum = 0;
    for (let j = 0; j < period; j++) {
      sum += data[i - j].close;
    }
    result.push({ time: data[i].time, value: sum / period });
  }
  return result;
}

function handleCrosshairMove(param) {
  if (!param || !param.time) {
    if (currentCandles.length > 0) {
      updateLegend(currentCandles[currentCandles.length - 1]);
    }
    return;
  }

  const cData = param.seriesData.get(candleSeries) || param.seriesData.get(areaSeries);
  if (cData) {
    const vData = param.seriesData.get(volumeSeries);
    updateLegend({
      open: cData.open !== undefined ? cData.open : cData.value,
      high: cData.high !== undefined ? cData.high : cData.value,
      low: cData.low !== undefined ? cData.low : cData.value,
      close: cData.close !== undefined ? cData.close : cData.value,
      volume: vData ? vData.value : 0
    });
  }
}

function updateLegend(candle) {
  const elO = document.getElementById('legend-open');
  const elH = document.getElementById('legend-high');
  const elL = document.getElementById('legend-low');
  const elC = document.getElementById('legend-close');
  const elV = document.getElementById('legend-vol');
  const elMcap = document.getElementById('legend-mcap');

  if (elO) elO.textContent = `$${formatPrice(candle.open)}`;
  if (elH) elH.textContent = `$${formatPrice(candle.high)}`;
  if (elL) elL.textContent = `$${formatPrice(candle.low)}`;
  if (elC) elC.textContent = `$${formatPrice(candle.close)}`;

  // Format volume: if candle volume is 0, use quote volume or realistic volume
  let vol = candle.volume;
  if ((!vol || vol <= 0) && latestQuote && latestQuote.volume > 0) {
    vol = latestQuote.volume;
  }
  if (elV) elV.textContent = formatVolume(vol || 0);

  // Format market cap in legend
  if (elMcap) {
    const supply = getCirculatingSupply(currentSymbol);
    const mcap = supply && candle.close ? (candle.close * supply) : (latestQuote && latestQuote.marketCap ? latestQuote.marketCap : 0);
    elMcap.textContent = formatMcap(mcap);
  }
}

function formatVolume(vol) {
  if (!vol || vol <= 0) return '0';
  if (vol >= 1_000_000_000) return (vol / 1_000_000_000).toFixed(2) + 'B';
  if (vol >= 1_000_000) return (vol / 1_000_000).toFixed(2) + 'M';
  if (vol >= 1_000) return (vol / 1_000).toFixed(1) + 'K';
  return vol.toString();
}

function tickActiveCandle(price) {
  if (!candleSeries || currentCandles.length === 0 || !price) return;
  const last = currentCandles[currentCandles.length - 1];

  // Safeguard: if price is drastically different from current candle close (e.g. cross-asset glitch), ignore
  if (last.close > 0 && (price > last.close * 20 || price < last.close / 20)) {
    return;
  }

  const updatedHigh = Math.max(last.high, price);
  const updatedLow = Math.min(last.low, price);
  last.high = updatedHigh;
  last.low = updatedLow;
  last.close = price;

  candleSeries.update({
    time: last.time,
    open: last.open,
    high: updatedHigh,
    low: updatedLow,
    close: price
  });

  if (areaSeries) {
    areaSeries.update({
      time: last.time,
      value: price
    });
  }

  updateLegend(last);
}

