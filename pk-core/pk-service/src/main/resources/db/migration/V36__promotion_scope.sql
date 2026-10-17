-- Phạm vi SKU của khuyến mãi sản phẩm (MARKDOWN / FLAT_PRICE) sinh từ chiến dịch wizard.
-- Trước đây chỉ có promotion_products (mức sản phẩm) nên chọn SKU A vẫn giảm giá cả SKU anh em cùng sản phẩm.
--   INCLUDE: chỉ các SKU liệt kê (trong các sản phẩm của khuyến mãi) mới được giảm (không có dòng INCLUDE = mọi SKU).
--   EXCLUDE: SKU bị loại trừ.
-- Khuyến mãi cũ (tạo qua /admin/promotions) không có dòng nào => hành vi không đổi.
CREATE TABLE promotion_scope_skus (
    promotion_id BIGINT      NOT NULL REFERENCES promotions (id) ON DELETE CASCADE,
    scope_type   VARCHAR(10) NOT NULL,
    sku_id       BIGINT      NOT NULL,
    PRIMARY KEY (promotion_id, scope_type, sku_id),
    CONSTRAINT ck_promotion_scope_type CHECK (scope_type IN ('INCLUDE', 'EXCLUDE'))
);
CREATE INDEX idx_promotion_scope_skus_sku ON promotion_scope_skus (sku_id);
