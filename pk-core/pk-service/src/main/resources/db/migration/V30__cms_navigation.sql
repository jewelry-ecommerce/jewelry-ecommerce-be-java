-- V30: menu điều hướng của website (CMS Navigation) với 2 lớp DRAFT (admin chỉnh) / PUBLISHED (storefront đọc).
-- Xuất bản = sao chép toàn bộ cây DRAFT (chưa lưu trữ) sang lớp PUBLISHED; source_id trỏ về id nút nháp để id công khai ổn định.
-- Badge (nhãn nhỏ) có lịch hiển thị riêng. Không chèn dữ liệu mẫu.
CREATE SEQUENCE cms_navigations_id_seq;
CREATE TABLE cms_navigations (
    id                  BIGINT PRIMARY KEY DEFAULT nextval('cms_navigations_id_seq'),
    layer               VARCHAR(10)  NOT NULL DEFAULT 'DRAFT',
    source_id           BIGINT,
    parent_id           BIGINT,
    label               VARCHAR(150) NOT NULL,
    type                VARCHAR(10)  NOT NULL DEFAULT 'NONE',
    target_id           VARCHAR(60),
    url                 VARCHAR(500),
    query_params        TEXT,
    icon                VARCHAR(500),
    image               VARCHAR(500),
    open_in_new_tab     BOOLEAN      NOT NULL DEFAULT FALSE,
    sort_order          INTEGER      NOT NULL DEFAULT 0,
    show_on_menu_popup  BOOLEAN      NOT NULL DEFAULT TRUE,
    show_on_plp         BOOLEAN      NOT NULL DEFAULT TRUE,
    is_archived         BOOLEAN      NOT NULL DEFAULT FALSE,
    created_id          BIGINT,
    created_date        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_id          BIGINT,
    updated_date        TIMESTAMPTZ,
    CONSTRAINT ck_cms_navigations_layer CHECK (layer IN ('DRAFT', 'PUBLISHED')),
    CONSTRAINT ck_cms_navigations_type CHECK (type IN ('NONE', 'CATEGORY', 'PAGE', 'LINK'))
);
ALTER SEQUENCE cms_navigations_id_seq OWNED BY cms_navigations.id;
CREATE INDEX ix_cms_navigations_layer ON cms_navigations (layer, parent_id, sort_order);

CREATE SEQUENCE cms_navigation_badges_id_seq;
CREATE TABLE cms_navigation_badges (
    id              BIGINT PRIMARY KEY DEFAULT nextval('cms_navigation_badges_id_seq'),
    layer           VARCHAR(10)  NOT NULL DEFAULT 'DRAFT',
    source_id       BIGINT,
    navigation_id   BIGINT       NOT NULL,
    design_type     VARCHAR(10)  NOT NULL,
    display_text    VARCHAR(20),
    image_url       VARCHAR(500),
    style_config    TEXT,
    start_time      TIMESTAMPTZ  NOT NULL,
    end_time        TIMESTAMPTZ,
    created_id      BIGINT,
    created_date    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_id      BIGINT,
    updated_date    TIMESTAMPTZ,
    CONSTRAINT ck_cms_nav_badges_layer CHECK (layer IN ('DRAFT', 'PUBLISHED')),
    CONSTRAINT ck_cms_nav_badges_design CHECK (design_type IN ('TEXT', 'IMAGE'))
);
ALTER SEQUENCE cms_navigation_badges_id_seq OWNED BY cms_navigation_badges.id;
CREATE INDEX ix_cms_nav_badges_nav ON cms_navigation_badges (navigation_id, layer);

-- Một dòng duy nhất: dấu vân tay của bản nháp tại lần xuất bản gần nhất (so với nháp hiện tại để biết còn thay đổi chưa xuất bản).
CREATE SEQUENCE cms_navigation_publish_id_seq;
CREATE TABLE cms_navigation_publish (
    id                  BIGINT PRIMARY KEY DEFAULT nextval('cms_navigation_publish_id_seq'),
    fingerprint         VARCHAR(64),
    published_date      TIMESTAMPTZ,
    created_id          BIGINT,
    created_date        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_id          BIGINT,
    updated_date        TIMESTAMPTZ
);
ALTER SEQUENCE cms_navigation_publish_id_seq OWNED BY cms_navigation_publish.id;
