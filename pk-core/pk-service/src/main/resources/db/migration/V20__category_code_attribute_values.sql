-- FE v5 (PIM): mã danh mục riêng, thuộc tính theo danh mục, giá trị thuộc tính có id/code/ảnh, trạng thái + dạng hiển thị thuộc tính.
-- Chỉ thêm (additive); backfill từ dữ liệu cũ, không xoá cột/bảng nào.

-- ---------- Danh mục: mã riêng (backfill = slug) ----------
ALTER TABLE categories ADD COLUMN code VARCHAR(140);
UPDATE categories SET code = slug WHERE code IS NULL;
-- Cột để NULL được: seed cũ (2. Data.sql) chưa có code; đọc thì coalesce(code, slug).
ALTER TABLE categories ADD CONSTRAINT uq_categories_code UNIQUE (code);

-- ---------- Thuộc tính gán trực tiếp cho danh mục (con kế thừa từ cha ở tầng service) ----------
CREATE SEQUENCE category_attributes_id_seq;
CREATE TABLE category_attributes (
    id            BIGINT PRIMARY KEY DEFAULT nextval('category_attributes_id_seq'),
    category_id   BIGINT NOT NULL REFERENCES categories (id) ON DELETE CASCADE,
    attribute_id  BIGINT NOT NULL REFERENCES product_attributes (id) ON DELETE CASCADE,
    created_id    BIGINT,
    created_date  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_category_attributes UNIQUE (category_id, attribute_id)
);
CREATE INDEX ix_category_attributes_attribute ON category_attributes (attribute_id);
ALTER SEQUENCE category_attributes_id_seq OWNED BY category_attributes.id;

-- ---------- Thuộc tính: trạng thái + dạng hiển thị ----------
ALTER TABLE product_attributes ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE';
ALTER TABLE product_attributes ADD COLUMN display_type VARCHAR(20) NOT NULL DEFAULT 'TEXT';
ALTER TABLE product_attributes ADD CONSTRAINT ck_product_attributes_status CHECK (status IN ('ACTIVE', 'INACTIVE'));
ALTER TABLE product_attributes ADD CONSTRAINT ck_product_attributes_display CHECK (display_type IN ('TEXT', 'IMAGE'));

-- ---------- Giá trị thuộc tính có id ổn định, code, ảnh ----------
CREATE SEQUENCE product_attribute_values_id_seq;
CREATE TABLE product_attribute_values (
    id            BIGINT PRIMARY KEY DEFAULT nextval('product_attribute_values_id_seq'),
    attribute_id  BIGINT NOT NULL REFERENCES product_attributes (id) ON DELETE CASCADE,
    code          VARCHAR(255) NOT NULL,
    value         VARCHAR(255) NOT NULL,
    image_url     VARCHAR(500),
    sort_order    INTEGER NOT NULL DEFAULT 0,
    created_id    BIGINT,
    created_date  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_id    BIGINT,
    updated_date  TIMESTAMPTZ,
    CONSTRAINT uq_product_attribute_values_code UNIQUE (attribute_id, code),
    CONSTRAINT uq_product_attribute_values_value UNIQUE (attribute_id, value)
);
CREATE INDEX ix_product_attribute_values_attribute ON product_attribute_values (attribute_id, sort_order);
ALTER SEQUENCE product_attribute_values_id_seq OWNED BY product_attribute_values.id;

-- Backfill từ cột options cũ (chuỗi nối bằng '||'): code = value = tên cũ (khớp quy ước FE "mã bằng tên").
INSERT INTO product_attribute_values (attribute_id, code, value, sort_order)
SELECT x.attribute_id, x.v, x.v, x.ord - 1
  FROM (SELECT DISTINCT ON (a.id, t.v) a.id AS attribute_id, t.v AS v, t.ord AS ord
          FROM product_attributes a
         CROSS JOIN LATERAL unnest(string_to_array(a.options, '||')) WITH ORDINALITY AS t(v, ord)
         WHERE a.options IS NOT NULL AND a.options <> '' AND t.v <> ''
         ORDER BY a.id, t.v, t.ord) x;
