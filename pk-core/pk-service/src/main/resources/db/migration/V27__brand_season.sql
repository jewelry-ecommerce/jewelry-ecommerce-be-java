-- V27: thương hiệu (brands) và mùa/bộ sưu tập theo mùa (seasons), gắn tuỳ chọn vào sản phẩm (products.brand_id / season_id).
-- Xoá thương hiệu/mùa là xoá mềm (deleted_date); mã (code) duy nhất trong số bản ghi chưa xoá, không phân biệt hoa/thường.
-- Không chèn dữ liệu mẫu.
CREATE SEQUENCE brands_id_seq;
CREATE TABLE brands (
    id            BIGINT PRIMARY KEY DEFAULT nextval('brands_id_seq'),
    code          VARCHAR(40)  NOT NULL,
    name          VARCHAR(150) NOT NULL,
    image_url     VARCHAR(500),
    website_url   VARCHAR(500),
    status        VARCHAR(10)  NOT NULL DEFAULT 'ACTIVE',
    created_id    BIGINT,
    created_date  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_id    BIGINT,
    updated_date  TIMESTAMPTZ,
    deleted_id    BIGINT,
    deleted_date  TIMESTAMPTZ,
    CONSTRAINT ck_brands_status CHECK (status IN ('ACTIVE', 'INACTIVE'))
);
ALTER SEQUENCE brands_id_seq OWNED BY brands.id;
CREATE UNIQUE INDEX uq_brands_code ON brands (UPPER(code)) WHERE deleted_date IS NULL;

CREATE SEQUENCE seasons_id_seq;
CREATE TABLE seasons (
    id            BIGINT PRIMARY KEY DEFAULT nextval('seasons_id_seq'),
    code          VARCHAR(40)  NOT NULL,
    name          VARCHAR(150) NOT NULL,
    description   VARCHAR(500),
    start_month   SMALLINT     NOT NULL,
    end_month     SMALLINT     NOT NULL,
    sort_order    INT          NOT NULL DEFAULT 0,
    is_active     BOOLEAN      NOT NULL DEFAULT TRUE,
    created_id    BIGINT,
    created_date  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_id    BIGINT,
    updated_date  TIMESTAMPTZ,
    deleted_id    BIGINT,
    deleted_date  TIMESTAMPTZ,
    CONSTRAINT ck_seasons_months CHECK (start_month BETWEEN 1 AND 12 AND end_month BETWEEN 1 AND 12)
);
ALTER SEQUENCE seasons_id_seq OWNED BY seasons.id;
CREATE UNIQUE INDEX uq_seasons_code ON seasons (UPPER(code)) WHERE deleted_date IS NULL;

ALTER TABLE products ADD COLUMN brand_id BIGINT REFERENCES brands (id);
ALTER TABLE products ADD COLUMN season_id BIGINT REFERENCES seasons (id);
CREATE INDEX idx_products_brand ON products (brand_id);
CREATE INDEX idx_products_season ON products (season_id);
