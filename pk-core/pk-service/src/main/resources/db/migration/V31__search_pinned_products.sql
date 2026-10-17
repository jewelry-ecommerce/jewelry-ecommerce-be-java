-- V31: sản phẩm ghim ở ô tìm kiếm. keyword_pattern NULL = ghim ở "landing" (khi chưa gõ từ khoá); có giá trị = ghim cho từ khoá đó.
-- position 0..3 (4 ô), duy nhất trong cùng phạm vi (landing hoặc cùng từ khoá) - service đảm bảo (đổi chỗ khi trùng lúc sửa).
CREATE SEQUENCE search_pinned_products_id_seq;
CREATE TABLE search_pinned_products (
    id               BIGINT PRIMARY KEY DEFAULT nextval('search_pinned_products_id_seq'),
    keyword_pattern  VARCHAR(200),
    product_id       BIGINT NOT NULL REFERENCES products (id),
    position         INTEGER NOT NULL DEFAULT 0,
    start_date       TIMESTAMPTZ,
    end_date         TIMESTAMPTZ,
    is_active        BOOLEAN NOT NULL DEFAULT TRUE,
    created_id       BIGINT,
    created_date     TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_id       BIGINT,
    updated_date     TIMESTAMPTZ,
    CONSTRAINT ck_search_pinned_position CHECK (position >= 0 AND position <= 3)
);
ALTER SEQUENCE search_pinned_products_id_seq OWNED BY search_pinned_products.id;
CREATE INDEX ix_search_pinned_scope ON search_pinned_products (keyword_pattern, position);
