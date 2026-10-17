-- Phạm vi SKU của voucher sinh từ chiến dịch khuyến mãi (wizard).
-- Trước đây voucher chỉ có mã/giá trị/điều kiện tối thiểu nên voucher của SKU A vẫn giảm cho giỏ chỉ có SKU B.
--   INCLUDE: chỉ các SKU liệt kê mới được giảm (không có dòng INCLUDE = không giới hạn theo SKU).
--   EXCLUDE: SKU bị loại trừ khỏi voucher.
-- Voucher cũ (tạo trực tiếp qua /admin/vouchers) không có dòng nào => hành vi không đổi.
CREATE TABLE voucher_scope_skus (
    voucher_id BIGINT      NOT NULL REFERENCES vouchers (id) ON DELETE CASCADE,
    scope_type VARCHAR(10) NOT NULL,
    sku_id     BIGINT      NOT NULL,
    PRIMARY KEY (voucher_id, scope_type, sku_id),
    CONSTRAINT ck_voucher_scope_type CHECK (scope_type IN ('INCLUDE', 'EXCLUDE'))
);
CREATE INDEX idx_voucher_scope_skus_sku ON voucher_scope_skus (sku_id);
