-- Additive fields: each MoMo order has one stable provider reference for safe retries.
ALTER TABLE payments ADD COLUMN gateway_order_id VARCHAR(100);
ALTER TABLE payments ADD COLUMN checkout_url TEXT;
CREATE UNIQUE INDEX uq_payments_gateway_order_id ON payments (gateway_order_id);
