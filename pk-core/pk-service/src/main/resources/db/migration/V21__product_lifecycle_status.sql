-- FE v5 (PIM): vòng đời sản phẩm đủ 5 trạng thái của UI gốc: DRAFT, UNPUBLISHED, PUBLISHED, DISCONTINUED, ARCHIVED.
-- Chỉ nới CHECK, không đổi dữ liệu cũ (sản phẩm DRAFT/PUBLISHED/ARCHIVED giữ nguyên).
ALTER TABLE products DROP CONSTRAINT ck_products_status;
ALTER TABLE products ADD CONSTRAINT ck_products_status
    CHECK (status IN ('DRAFT', 'UNPUBLISHED', 'PUBLISHED', 'DISCONTINUED', 'ARCHIVED'));
