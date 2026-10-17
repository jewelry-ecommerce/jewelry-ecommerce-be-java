-- Snapshot SKU tại thời điểm đặt hàng: đổi mã/thuộc tính SKU sau này không làm đổi đơn cũ.
ALTER TABLE order_items ADD COLUMN IF NOT EXISTS sku_code VARCHAR(100);
ALTER TABLE order_items ADD COLUMN IF NOT EXISTS size_label VARCHAR(100);
ALTER TABLE order_items ADD COLUMN IF NOT EXISTS metal_color_label VARCHAR(100);
-- Backfill đơn cũ từ SKU hiện tại (best effort, chỉ khi còn trống).
UPDATE order_items oi SET sku_code = s.sku_code, size_label = s.size_label, metal_color_label = s.metal_color_label
  FROM product_skus s WHERE s.id = oi.sku_id AND oi.sku_code IS NULL;
