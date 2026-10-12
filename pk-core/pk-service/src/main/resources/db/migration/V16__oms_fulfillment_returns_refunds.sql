-- V16: chốt xuất tồn khi giao (stock_fulfilled), trả hàng theo số lượng từng dòng, hoàn tiền từng phần.
-- Không đụng dữ liệu cũ: đơn DELIVERED cũ giữ stock_fulfilled = FALSE (khi trả hàng sẽ nhả reserved như trước).
ALTER TABLE orders ADD COLUMN IF NOT EXISTS stock_fulfilled BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE orders ADD COLUMN IF NOT EXISTS refunded_amount NUMERIC(15, 0) NOT NULL DEFAULT 0;
ALTER TABLE orders ADD CONSTRAINT ck_orders_refunded CHECK (refunded_amount >= 0);
ALTER TABLE order_items ADD COLUMN IF NOT EXISTS returned_quantity INTEGER NOT NULL DEFAULT 0;
ALTER TABLE order_items ADD CONSTRAINT ck_order_items_returned CHECK (returned_quantity >= 0 AND returned_quantity <= quantity);
-- Đơn đã hoàn tiền toàn bộ trước migration: ghi nhận số đã hoàn = tổng đơn.
UPDATE orders SET refunded_amount = grand_total WHERE payment_status = 'REFUNDED' AND refunded_amount = 0;
