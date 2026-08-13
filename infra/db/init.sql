-- ═══════════════════════════════════════════════════════════════
--  Polyglot Trading Platform — Database Schema
--  Auto-executed by PostgreSQL on first container startup.
-- ═══════════════════════════════════════════════════════════════

-- ─── Users ───────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS users (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    username    VARCHAR(50)  UNIQUE NOT NULL,
    email       VARCHAR(255) UNIQUE NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- ─── Portfolios ───────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS portfolios (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    cash        NUMERIC(18, 8) NOT NULL DEFAULT 100000.00,  -- Starting balance
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    UNIQUE (user_id)
);

-- ─── Positions (holdings per asset) ──────────────────────────────
CREATE TABLE IF NOT EXISTS positions (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    portfolio_id  UUID           NOT NULL REFERENCES portfolios(id) ON DELETE CASCADE,
    symbol        VARCHAR(20)    NOT NULL,
    quantity      NUMERIC(18, 8) NOT NULL DEFAULT 0,
    average_cost  NUMERIC(18, 8) NOT NULL DEFAULT 0,
    updated_at    TIMESTAMPTZ    NOT NULL DEFAULT now(),
    UNIQUE (portfolio_id, symbol)
);

-- ─── Orders ───────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS orders (
    id           UUID PRIMARY KEY,
    user_id      UUID           NOT NULL REFERENCES users(id),
    symbol       VARCHAR(20)    NOT NULL,
    side         VARCHAR(4)     NOT NULL CHECK (side IN ('BUY', 'SELL')),
    type         VARCHAR(6)     NOT NULL CHECK (type IN ('LIMIT', 'MARKET')),
    quantity     NUMERIC(18, 8) NOT NULL,
    price        NUMERIC(18, 8),                  -- NULL for MARKET orders
    status       VARCHAR(20)    NOT NULL DEFAULT 'PENDING',
    created_at   TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ    NOT NULL DEFAULT now()
);

-- ─── Trades (matched executions) ─────────────────────────────────
CREATE TABLE IF NOT EXISTS trades (
    id             UUID PRIMARY KEY,
    buy_order_id   UUID           NOT NULL REFERENCES orders(id),
    sell_order_id  UUID           NOT NULL REFERENCES orders(id),
    symbol         VARCHAR(20)    NOT NULL,
    quantity       NUMERIC(18, 8) NOT NULL,
    price          NUMERIC(18, 8) NOT NULL,
    executed_at    TIMESTAMPTZ    NOT NULL DEFAULT now()
);

-- ─── Indexes ──────────────────────────────────────────────────────
CREATE INDEX IF NOT EXISTS idx_orders_user_id   ON orders(user_id);
CREATE INDEX IF NOT EXISTS idx_orders_symbol    ON orders(symbol);
CREATE INDEX IF NOT EXISTS idx_orders_status    ON orders(status);
CREATE INDEX IF NOT EXISTS idx_trades_symbol    ON trades(symbol);
CREATE INDEX IF NOT EXISTS idx_trades_executed  ON trades(executed_at DESC);

-- ─── Seed: demo user ──────────────────────────────────────────────
INSERT INTO users (id, username, email)
VALUES ('00000000-0000-0000-0000-000000000001', 'demo', 'demo@trading.local')
ON CONFLICT DO NOTHING;

INSERT INTO portfolios (user_id, cash)
VALUES ('00000000-0000-0000-0000-000000000001', 100000.00)
ON CONFLICT DO NOTHING;
