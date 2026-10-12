-- V18: dữ liệu PIM mà form admin cần lưu (chỉ THÊM cột/bảng, không sửa/xoá dữ liệu cũ).
--   products: unit_of_measure, tax_rate_override (%), weight_gram/length/width/height, meta_title/description/keywords.
--   product_skus: cost_price, b2b_wholesale_price, tax_rate_override (%), length/width/height.
--   product_infos: khối thông tin bổ sung (title, description, sort_order). product_mix_matches: sản phẩm phối kèm.
ALTER TABLE products ADD COLUMN unit_of_measure VARCHAR(20) NOT NULL DEFAULT 'EACH';
ALTER TABLE products ADD COLUMN tax_rate_override NUMERIC(5, 2);
ALTER TABLE products ADD COLUMN weight_gram NUMERIC(10, 2);
ALTER TABLE products ADD COLUMN length NUMERIC(10, 2);
ALTER TABLE products ADD COLUMN width NUMERIC(10, 2);
ALTER TABLE products ADD COLUMN height NUMERIC(10, 2);
ALTER TABLE products ADD COLUMN meta_title VARCHAR(200);
ALTER TABLE products ADD COLUMN meta_description VARCHAR(500);
ALTER TABLE products ADD COLUMN meta_keywords VARCHAR(500);
ALTER TABLE products ADD CONSTRAINT ck_products_unit CHECK (unit_of_measure IN ('EACH', 'STRAND', 'PAIR', 'SET', 'BOX', 'BAG', 'SHEET'));
ALTER TABLE products ADD CONSTRAINT ck_products_tax CHECK (tax_rate_override IS NULL OR (tax_rate_override >= 0 AND tax_rate_override <= 100));

ALTER TABLE product_skus ADD COLUMN cost_price NUMERIC(15, 0);
ALTER TABLE product_skus ADD COLUMN b2b_wholesale_price NUMERIC(15, 0);
ALTER TABLE product_skus ADD COLUMN tax_rate_override NUMERIC(5, 2);
ALTER TABLE product_skus ADD COLUMN length NUMERIC(10, 2);
ALTER TABLE product_skus ADD COLUMN width NUMERIC(10, 2);
ALTER TABLE product_skus ADD COLUMN height NUMERIC(10, 2);
ALTER TABLE product_skus ADD CONSTRAINT ck_product_skus_cost CHECK (cost_price IS NULL OR cost_price >= 0);
ALTER TABLE product_skus ADD CONSTRAINT ck_product_skus_b2b CHECK (b2b_wholesale_price IS NULL OR b2b_wholesale_price >= 0);
ALTER TABLE product_skus ADD CONSTRAINT ck_product_skus_tax CHECK (tax_rate_override IS NULL OR (tax_rate_override >= 0 AND tax_rate_override <= 100));

CREATE SEQUENCE product_infos_id_seq;
CREATE TABLE product_infos (
    id            BIGINT PRIMARY KEY DEFAULT nextval('product_infos_id_seq'),
    product_id    BIGINT NOT NULL REFERENCES products (id) ON DELETE CASCADE,
    title         VARCHAR(200),
    description   VARCHAR(4000) NOT NULL,
    sort_order    INTEGER NOT NULL DEFAULT 0,
    created_id    BIGINT,
    created_date  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_product_infos_product ON product_infos (product_id, sort_order);
ALTER SEQUENCE product_infos_id_seq OWNED BY product_infos.id;

CREATE SEQUENCE product_mix_matches_id_seq;
CREATE TABLE product_mix_matches (
    id                  BIGINT PRIMARY KEY DEFAULT nextval('product_mix_matches_id_seq'),
    product_id          BIGINT NOT NULL REFERENCES products (id) ON DELETE CASCADE,
    related_product_id  BIGINT NOT NULL REFERENCES products (id) ON DELETE CASCADE,
    sort_order          INTEGER NOT NULL DEFAULT 0,
    created_id          BIGINT,
    created_date        TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_product_mix_matches UNIQUE (product_id, related_product_id),
    CONSTRAINT ck_product_mix_matches_self CHECK (product_id <> related_product_id)
);
CREATE INDEX ix_product_mix_matches_product ON product_mix_matches (product_id, sort_order);
ALTER SEQUENCE product_mix_matches_id_seq OWNED BY product_mix_matches.id;
