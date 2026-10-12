-- V19: phạm vi đặt trước (pre-order). Cấu hình cũ giữ nguyên hành vi (scope_type = 'ALL': mọi SKU HẾT HÀNG đặt trước được).
--   scope_type SKU | PRODUCT | CATEGORY | COLLECTION + scope_ids (danh sách id, ngăn cách bởi dấu phẩy): chỉ các SKU thuộc phạm vi
--   này là hàng đặt trước (hiện nhãn PRE_ORDER kể cả còn hàng; hết hàng thì nhận đơn đặt trước). SKU hết hàng ngoài phạm vi = OUT_OF_STOCK.
ALTER TABLE pre_order_configs ADD COLUMN scope_type VARCHAR(20) NOT NULL DEFAULT 'ALL';
ALTER TABLE pre_order_configs ADD COLUMN scope_ids VARCHAR(2000);
ALTER TABLE pre_order_configs ADD CONSTRAINT ck_pre_order_scope_type
    CHECK (scope_type IN ('ALL', 'SKU', 'PRODUCT', 'CATEGORY', 'COLLECTION'));
