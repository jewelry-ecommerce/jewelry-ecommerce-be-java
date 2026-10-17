-- V32: cấu hình banner chèn vào danh sách sản phẩm theo điều kiện (danh mục hoặc trang CMS storefront).
-- banners là mảng JSON các banner (ảnh, lịch, hành động LINK_URL / MIX_MATCH). Mỗi (condition_type, target_slug) có tối đa một cấu hình.
CREATE SEQUENCE product_list_banner_configs_id_seq;
CREATE TABLE product_list_banner_configs (
    id                   BIGINT PRIMARY KEY DEFAULT nextval('product_list_banner_configs_id_seq'),
    condition_type       VARCHAR(20)  NOT NULL,
    target_id            VARCHAR(60),
    target_slug          VARCHAR(200) NOT NULL,
    target_name          VARCHAR(200),
    category_path_slugs  TEXT,
    is_active            BOOLEAN NOT NULL DEFAULT TRUE,
    banners              TEXT,
    created_id           BIGINT,
    created_date         TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_id           BIGINT,
    updated_date         TIMESTAMPTZ,
    CONSTRAINT ck_plbc_condition CHECK (condition_type IN ('CATEGORY', 'CMS_STOREFRONT'))
);
ALTER SEQUENCE product_list_banner_configs_id_seq OWNED BY product_list_banner_configs.id;
CREATE UNIQUE INDEX uq_plbc_target ON product_list_banner_configs (condition_type, target_slug);
