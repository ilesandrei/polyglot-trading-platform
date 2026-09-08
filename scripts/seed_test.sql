INSERT INTO users (id, username, email) VALUES ('00000000-0000-0000-0000-000000000001', 'test_buyer', 'buyer@test.local') ON CONFLICT DO NOTHING;
INSERT INTO users (id, username, email) VALUES ('00000000-0000-0000-0000-000000000002', 'test_seller', 'seller@test.local') ON CONFLICT DO NOTHING;

INSERT INTO portfolios (user_id, cash) VALUES ('00000000-0000-0000-0000-000000000001', 100000.00) 
    ON CONFLICT (user_id) DO UPDATE SET cash = 100000.00;

INSERT INTO portfolios (user_id, cash) VALUES ('00000000-0000-0000-0000-000000000002', 50000.00) 
    ON CONFLICT (user_id) DO UPDATE SET cash = 50000.00;

DELETE FROM positions WHERE symbol = 'MSFT' AND portfolio_id IN (
    SELECT id FROM portfolios WHERE user_id IN ('00000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000002')
);

INSERT INTO positions (portfolio_id, symbol, quantity, average_cost) 
    SELECT id, 'MSFT', 100.0, 200.00 FROM portfolios WHERE user_id = '00000000-0000-0000-0000-000000000002';
