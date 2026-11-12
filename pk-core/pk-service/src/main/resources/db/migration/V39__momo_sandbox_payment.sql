-- V39: giữ cột payment gateway (đã có thể đã apply trên Neon).
-- Code MoMo đã gỡ; cột để trống / dùng cổng khác sau này. Không DROP để tránh Flyway/data risk.
ALTER TABLE payments ADD COLUMN IF NOT EXISTS gateway_order_id VARCHAR(100);
ALTER TABLE payments ADD COLUMN IF NOT EXISTS checkout_url TEXT;
CREATE UNIQUE INDEX IF NOT EXISTS uq_payments_gateway_order_id ON payments (gateway_order_id);
