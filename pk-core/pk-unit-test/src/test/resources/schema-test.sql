-- Schema cho test trên H2: tương đương db/migration/V1__init.sql (thiết kế mới 2026-09-29) nhưng
-- dùng cú pháp H2. Khoá chính dùng SEQUENCE tên <bảng>_id_seq (giống sequence PostgreSQL tự tạo cho
-- cột IDENTITY), vì Mirage lấy id bằng SELECT NEXTVAL('<bảng>_id_seq') trước khi insert. Bảng nối
-- (user_roles, product_collections, promotion_products) không cần sequence (không có cột id riêng).
-- Không tạo index (test không cần, giữ file gọn - giống quy ước bản trước).

CREATE SEQUENCE roles_id_seq START WITH 1;
CREATE SEQUENCE users_id_seq START WITH 1;
CREATE SEQUENCE refresh_tokens_id_seq START WITH 1;
CREATE SEQUENCE customer_addresses_id_seq START WITH 1;
CREATE SEQUENCE categories_id_seq START WITH 1;
CREATE SEQUENCE collections_id_seq START WITH 1;
CREATE SEQUENCE products_id_seq START WITH 1;
CREATE SEQUENCE product_skus_id_seq START WITH 1;
CREATE SEQUENCE orders_id_seq START WITH 1;
CREATE SEQUENCE order_items_id_seq START WITH 1;
CREATE SEQUENCE order_timeline_id_seq START WITH 1;
CREATE SEQUENCE payments_id_seq START WITH 1;
CREATE SEQUENCE vouchers_id_seq START WITH 1;
CREATE SEQUENCE promotions_id_seq START WITH 1;
CREATE SEQUENCE phone_otps_id_seq START WITH 1;
CREATE SEQUENCE carts_id_seq START WITH 1;
CREATE SEQUENCE cart_items_id_seq START WITH 1;
CREATE SEQUENCE sku_attribute_values_id_seq START WITH 1;
CREATE SEQUENCE product_media_id_seq START WITH 1;
CREATE SEQUENCE product_infos_id_seq START WITH 1;
CREATE SEQUENCE product_approval_requests_id_seq START WITH 1;
CREATE SEQUENCE import_export_jobs_id_seq START WITH 1;
CREATE SEQUENCE import_job_rows_id_seq START WITH 1;
CREATE SEQUENCE order_idempotency_keys_id_seq START WITH 1;
CREATE SEQUENCE site_headers_id_seq START WITH 1;
CREATE SEQUENCE site_footer_id_seq START WITH 1;
CREATE SEQUENCE site_logos_id_seq START WITH 1;
CREATE SEQUENCE brands_id_seq START WITH 1;
CREATE SEQUENCE seasons_id_seq START WITH 1;
CREATE SEQUENCE category_attributes_id_seq START WITH 1;
CREATE SEQUENCE product_attribute_values_id_seq START WITH 1;
CREATE SEQUENCE product_mix_matches_id_seq START WITH 1;
CREATE SEQUENCE wishlist_items_id_seq START WITH 1;
CREATE SEQUENCE banner_placements_id_seq START WITH 1;
CREATE SEQUENCE banners_id_seq START WITH 1;
CREATE SEQUENCE banner_placement_layouts_id_seq START WITH 1;
CREATE SEQUENCE banner_placement_layout_items_id_seq START WITH 1;
CREATE SEQUENCE customer_requests_id_seq START WITH 1;
CREATE SEQUENCE pre_order_configs_id_seq START WITH 1;

-- ===================== IDENTITY =====================
CREATE TABLE roles (
    id           BIGINT PRIMARY KEY,
    name         VARCHAR(30) NOT NULL,
    created_id   BIGINT,
    created_date TIMESTAMP NOT NULL,
    CONSTRAINT uq_roles_name UNIQUE (name)
);

CREATE TABLE users (
    id             BIGINT PRIMARY KEY,
    phone          VARCHAR(20) NOT NULL,
    email          VARCHAR(254),
    password_hash  VARCHAR(100) NOT NULL,
    full_name      VARCHAR(120) NOT NULL,
    enabled        BOOLEAN NOT NULL DEFAULT TRUE,
    created_id     BIGINT,
    created_date   TIMESTAMP NOT NULL,
    updated_id     BIGINT,
    updated_date   TIMESTAMP,
    deleted_id     BIGINT,
    deleted_date   TIMESTAMP,
    CONSTRAINT uq_users_phone UNIQUE (phone),
    CONSTRAINT uq_users_email UNIQUE (email),
    CONSTRAINT ck_users_email_lowercase CHECK (email = lower(email))
);

CREATE TABLE user_roles (
    user_id  BIGINT NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    role_id  BIGINT NOT NULL REFERENCES roles (id),
    PRIMARY KEY (user_id, role_id)
);

CREATE TABLE refresh_tokens (
    id           BIGINT PRIMARY KEY,
    user_id      BIGINT NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash   VARCHAR(64) NOT NULL,
    family_id    VARCHAR(36) NOT NULL,
    expires_at   TIMESTAMP NOT NULL,
    revoked_at   TIMESTAMP,
    created_id   BIGINT,
    created_date TIMESTAMP NOT NULL,
    updated_id   BIGINT,
    updated_date TIMESTAMP,
    CONSTRAINT uq_refresh_tokens_hash UNIQUE (token_hash)
);

-- ===================== ĐỊA CHỈ GIAO HÀNG =====================
CREATE TABLE customer_addresses (
    id             BIGINT PRIMARY KEY,
    user_id        BIGINT NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    recipient_name VARCHAR(120) NOT NULL,
    phone          VARCHAR(20) NOT NULL,
    province       VARCHAR(100) NOT NULL,
    district       VARCHAR(100) NOT NULL,
    ward           VARCHAR(100) NOT NULL,
    address_line   VARCHAR(255) NOT NULL,
    is_default     BOOLEAN NOT NULL DEFAULT FALSE,
    created_id     BIGINT,
    created_date   TIMESTAMP NOT NULL,
    updated_id     BIGINT,
    updated_date   TIMESTAMP,
    deleted_id     BIGINT,
    deleted_date   TIMESTAMP
);

-- ===================== CATALOG =====================
CREATE TABLE categories (
    id           BIGINT PRIMARY KEY,
    parent_id    BIGINT REFERENCES categories (id),
    name         VARCHAR(120) NOT NULL,
    slug         VARCHAR(140) NOT NULL,
    code         VARCHAR(140),
    description  VARCHAR(500),
    image_url    VARCHAR(500),
    sort_order   INTEGER NOT NULL DEFAULT 0,
    active       BOOLEAN NOT NULL DEFAULT TRUE,
    created_id   BIGINT,
    created_date TIMESTAMP NOT NULL,
    updated_id   BIGINT,
    updated_date TIMESTAMP,
    deleted_id   BIGINT,
    deleted_date TIMESTAMP,
    CONSTRAINT uq_categories_slug UNIQUE (slug),
    CONSTRAINT uq_categories_code UNIQUE (code)
);

CREATE TABLE collections (
    id             BIGINT PRIMARY KEY,
    name           VARCHAR(150) NOT NULL,
    slug           VARCHAR(170) NOT NULL,
    description    VARCHAR(1000),
    hero_image_url VARCHAR(500),
    status         VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    created_id     BIGINT,
    created_date   TIMESTAMP NOT NULL,
    updated_id     BIGINT,
    updated_date   TIMESTAMP,
    deleted_id     BIGINT,
    deleted_date   TIMESTAMP,
    CONSTRAINT uq_collections_slug UNIQUE (slug),
    CONSTRAINT ck_collections_status CHECK (status IN ('DRAFT', 'PUBLISHED'))
);

CREATE TABLE brands (
    id           BIGINT PRIMARY KEY,
    code         VARCHAR(40)  NOT NULL,
    name         VARCHAR(150) NOT NULL,
    image_url    VARCHAR(500),
    website_url  VARCHAR(500),
    status       VARCHAR(10)  NOT NULL,
    created_id   BIGINT,
    created_date TIMESTAMP    NOT NULL,
    updated_id   BIGINT,
    updated_date TIMESTAMP,
    deleted_id   BIGINT,
    deleted_date TIMESTAMP
);

CREATE TABLE seasons (
    id           BIGINT PRIMARY KEY,
    code         VARCHAR(40)  NOT NULL,
    name         VARCHAR(150) NOT NULL,
    description  VARCHAR(500),
    start_month  SMALLINT     NOT NULL,
    end_month    SMALLINT     NOT NULL,
    sort_order   INT          NOT NULL,
    is_active    BOOLEAN      NOT NULL,
    created_id   BIGINT,
    created_date TIMESTAMP    NOT NULL,
    updated_id   BIGINT,
    updated_date TIMESTAMP,
    deleted_id   BIGINT,
    deleted_date TIMESTAMP
);

CREATE TABLE products (
    brand_id          BIGINT REFERENCES brands (id),
    season_id         BIGINT REFERENCES seasons (id),
    id                BIGINT PRIMARY KEY,
    category_id       BIGINT NOT NULL REFERENCES categories (id),
    code              VARCHAR(60) NOT NULL,
    name              VARCHAR(200) NOT NULL,
    slug              VARCHAR(220) NOT NULL,
    short_description VARCHAR(300),
    description       VARCHAR(2000),
    material          VARCHAR(80),
    occasion          VARCHAR(80),
    thumbnail_url     VARCHAR(500),
    unit_of_measure   VARCHAR(20) NOT NULL DEFAULT 'EACH',
    tax_rate_override NUMERIC(5, 2),
    weight_gram       NUMERIC(10, 2),
    length            NUMERIC(10, 2),
    width             NUMERIC(10, 2),
    height            NUMERIC(10, 2),
    meta_title        VARCHAR(200),
    meta_description  VARCHAR(500),
    meta_keywords     VARCHAR(500),
    base_price        NUMERIC(15, 0) NOT NULL DEFAULT 0,
    status            VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    published_at      TIMESTAMP,
    version           BIGINT NOT NULL DEFAULT 0,
    created_id        BIGINT,
    created_date      TIMESTAMP NOT NULL,
    updated_id        BIGINT,
    updated_date      TIMESTAMP,
    deleted_id        BIGINT,
    deleted_date      TIMESTAMP,
    CONSTRAINT ck_products_unit CHECK (unit_of_measure IN ('EACH', 'STRAND', 'PAIR', 'SET', 'BOX', 'BAG', 'SHEET')),
    CONSTRAINT ck_products_tax CHECK (tax_rate_override IS NULL OR (tax_rate_override >= 0 AND tax_rate_override <= 100)),
    CONSTRAINT uq_products_code UNIQUE (code),
    CONSTRAINT uq_products_slug UNIQUE (slug),
    CONSTRAINT ck_products_price CHECK (base_price >= 0),
    CONSTRAINT ck_products_status CHECK (status IN ('DRAFT', 'UNPUBLISHED', 'PUBLISHED', 'DISCONTINUED', 'ARCHIVED')),
    CONSTRAINT ck_products_published CHECK (status <> 'PUBLISHED' OR published_at IS NOT NULL)
);

CREATE TABLE product_collections (
    product_id    BIGINT NOT NULL REFERENCES products (id) ON DELETE CASCADE,
    collection_id BIGINT NOT NULL REFERENCES collections (id) ON DELETE CASCADE,
    PRIMARY KEY (product_id, collection_id)
);

CREATE TABLE product_skus (
    id                BIGINT PRIMARY KEY,
    product_id        BIGINT NOT NULL REFERENCES products (id) ON DELETE CASCADE,
    sku_code          VARCHAR(64) NOT NULL,
    name              VARCHAR(150),
    status            VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    material          VARCHAR(80),
    gemstone          VARCHAR(80),
    size_label        VARCHAR(40),
    metal_color_label VARCHAR(40),
    carat_weight      NUMERIC(6, 2),
    weight_gram       NUMERIC(8, 2),
    list_price        NUMERIC(15, 0) NOT NULL,
    sale_price        NUMERIC(15, 0),
    cost_price        NUMERIC(15, 0),
    b2b_wholesale_price NUMERIC(15, 0),
    tax_rate_override NUMERIC(5, 2),
    length            NUMERIC(10, 2),
    width             NUMERIC(10, 2),
    height            NUMERIC(10, 2),
    on_hand           INTEGER NOT NULL DEFAULT 0,
    reserved          INTEGER NOT NULL DEFAULT 0,
    is_default        BOOLEAN NOT NULL DEFAULT FALSE,
    version           BIGINT NOT NULL DEFAULT 0,
    created_id        BIGINT,
    created_date      TIMESTAMP NOT NULL,
    updated_id        BIGINT,
    updated_date      TIMESTAMP,
    deleted_id        BIGINT,
    deleted_date      TIMESTAMP,
    CONSTRAINT ck_product_skus_cost CHECK (cost_price IS NULL OR cost_price >= 0),
    CONSTRAINT ck_product_skus_b2b CHECK (b2b_wholesale_price IS NULL OR b2b_wholesale_price >= 0),
    CONSTRAINT ck_product_skus_tax CHECK (tax_rate_override IS NULL OR (tax_rate_override >= 0 AND tax_rate_override <= 100)),
    CONSTRAINT uq_product_skus_code UNIQUE (sku_code),
    CONSTRAINT ck_product_skus_status CHECK (status IN ('DRAFT', 'PUBLISHED', 'ARCHIVED')),
    CONSTRAINT ck_product_skus_list_price CHECK (list_price >= 0),
    CONSTRAINT ck_product_skus_sale_price CHECK (sale_price IS NULL OR sale_price >= 0),
    CONSTRAINT ck_product_skus_on_hand CHECK (on_hand >= 0),
    CONSTRAINT ck_product_skus_reserved CHECK (reserved >= 0)
    -- Không ràng buộc reserved <= on_hand: đơn đặt trước (pre-order) cho phép giữ chỗ vượt tồn kho.
);

-- ===================== ĐƠN HÀNG =====================
CREATE TABLE orders (
    id                  BIGINT PRIMARY KEY,
    code                VARCHAR(30) NOT NULL,
    user_id             BIGINT REFERENCES users (id),
    guest_email         VARCHAR(150),
    status              VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    payment_status      VARCHAR(20) NOT NULL DEFAULT 'UNPAID',
    payment_method      VARCHAR(30) NOT NULL,
    shipping_method     VARCHAR(30) NOT NULL,
    tracking_code       VARCHAR(60),
    ship_recipient_name VARCHAR(120) NOT NULL,
    ship_phone          VARCHAR(20) NOT NULL,
    ship_province       VARCHAR(100) NOT NULL,
    ship_district       VARCHAR(100) NOT NULL,
    ship_ward           VARCHAR(100) NOT NULL,
    ship_address_line   VARCHAR(255) NOT NULL,
    subtotal            NUMERIC(15, 0) NOT NULL,
    product_discount    NUMERIC(15, 0) NOT NULL DEFAULT 0,
    voucher_discount    NUMERIC(15, 0) NOT NULL DEFAULT 0,
    shipping_fee        NUMERIC(15, 0) NOT NULL DEFAULT 0,
    grand_total         NUMERIC(15, 0) NOT NULL,
    stock_fulfilled     BOOLEAN NOT NULL DEFAULT FALSE,
    refunded_amount     NUMERIC(15, 0) NOT NULL DEFAULT 0,
    applied_voucher_code VARCHAR(40),
    note                VARCHAR(500),
    placed_at           TIMESTAMP NOT NULL,
    created_id          BIGINT,
    created_date        TIMESTAMP NOT NULL,
    updated_id          BIGINT,
    updated_date        TIMESTAMP,
    CONSTRAINT uq_orders_code UNIQUE (code),
    CONSTRAINT ck_orders_status CHECK (status IN ('PENDING', 'CONFIRMED', 'SHIPPING', 'DELIVERED', 'CANCELLED', 'RETURNED')),
    CONSTRAINT ck_orders_payment_status CHECK (payment_status IN ('UNPAID', 'PAID', 'REFUNDED')),
    CONSTRAINT ck_orders_grand_total CHECK (grand_total >= 0),
    CONSTRAINT ck_orders_refunded CHECK (refunded_amount >= 0)
);

CREATE TABLE order_items (
    id           BIGINT PRIMARY KEY,
    order_id     BIGINT NOT NULL REFERENCES orders (id) ON DELETE CASCADE,
    product_id   BIGINT NOT NULL REFERENCES products (id),
    sku_id       BIGINT NOT NULL REFERENCES product_skus (id),
    name         VARCHAR(200) NOT NULL,
    image_url    VARCHAR(500),
    quantity     INTEGER NOT NULL,
    unit_price   NUMERIC(15, 0) NOT NULL,
    line_total   NUMERIC(15, 0) NOT NULL,
    returned_quantity INTEGER NOT NULL DEFAULT 0,
    created_id   BIGINT,
    created_date TIMESTAMP NOT NULL,
    CONSTRAINT ck_order_items_quantity CHECK (quantity > 0),
    CONSTRAINT ck_order_items_unit_price CHECK (unit_price >= 0),
    CONSTRAINT ck_order_items_returned CHECK (returned_quantity >= 0 AND returned_quantity <= quantity)
);

CREATE TABLE order_timeline (
    id           BIGINT PRIMARY KEY,
    order_id     BIGINT NOT NULL REFERENCES orders (id) ON DELETE CASCADE,
    status       VARCHAR(20) NOT NULL,
    occurred_at  TIMESTAMP NOT NULL,
    note         VARCHAR(500),
    actor        VARCHAR(60),
    created_id   BIGINT,
    created_date TIMESTAMP NOT NULL
);

-- ===================== THANH TOÁN =====================
CREATE TABLE payments (
    id             BIGINT PRIMARY KEY,
    order_id       BIGINT NOT NULL REFERENCES orders (id),
    provider       VARCHAR(30) NOT NULL,
    transaction_id VARCHAR(100),
    amount         NUMERIC(15, 0) NOT NULL,
    status         VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    signature      VARCHAR(500),
    created_id     BIGINT,
    created_date   TIMESTAMP NOT NULL,
    updated_id     BIGINT,
    updated_date   TIMESTAMP,
    CONSTRAINT ck_payments_amount CHECK (amount >= 0),
    CONSTRAINT ck_payments_status CHECK (status IN ('PENDING', 'SUCCESS', 'FAILED'))
);

-- ===================== VOUCHER & KHUYẾN MÃI =====================
CREATE TABLE vouchers (
    id                  BIGINT PRIMARY KEY,
    code                VARCHAR(40) NOT NULL,
    discount_type       VARCHAR(20) NOT NULL,
    discount_value      NUMERIC(15, 0) NOT NULL,
    max_discount_amount NUMERIC(15, 0),
    min_order_value     NUMERIC(15, 0) NOT NULL DEFAULT 0,
    usage_limit         INTEGER,
    used_count          INTEGER NOT NULL DEFAULT 0,
    starts_at           TIMESTAMP NOT NULL,
    ends_at             TIMESTAMP NOT NULL,
    status              VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    created_id          BIGINT,
    created_date        TIMESTAMP NOT NULL,
    updated_id          BIGINT,
    updated_date        TIMESTAMP,
    deleted_id          BIGINT,
    deleted_date        TIMESTAMP,
    CONSTRAINT uq_vouchers_code UNIQUE (code),
    CONSTRAINT ck_vouchers_discount_type CHECK (discount_type IN ('PERCENT', 'FIXED')),
    CONSTRAINT ck_vouchers_status CHECK (status IN ('DRAFT', 'PUBLISHED')),
    CONSTRAINT ck_vouchers_period CHECK (ends_at > starts_at)
);

CREATE TABLE promotions (
    id                  BIGINT PRIMARY KEY,
    name                VARCHAR(150) NOT NULL,
    discount_type       VARCHAR(20) NOT NULL,
    discount_value      NUMERIC(15, 0) NOT NULL,
    max_discount_amount NUMERIC(15, 0),
    starts_at           TIMESTAMP NOT NULL,
    ends_at             TIMESTAMP NOT NULL,
    status              VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    created_id          BIGINT,
    created_date        TIMESTAMP NOT NULL,
    updated_id          BIGINT,
    updated_date        TIMESTAMP,
    deleted_id          BIGINT,
    deleted_date        TIMESTAMP,
    CONSTRAINT ck_promotions_discount_type CHECK (discount_type IN ('PERCENT', 'FIXED', 'FLAT_PRICE')),
    CONSTRAINT ck_promotions_status CHECK (status IN ('DRAFT', 'PUBLISHED')),
    CONSTRAINT ck_promotions_period CHECK (ends_at > starts_at)
);

CREATE TABLE promotion_products (
    promotion_id BIGINT NOT NULL REFERENCES promotions (id) ON DELETE CASCADE,
    product_id   BIGINT NOT NULL REFERENCES products (id) ON DELETE CASCADE,
    PRIMARY KEY (promotion_id, product_id)
);

-- ===================== V2: THUỘC TÍNH SẢN PHẨM / CMS / BADGE (Admin) =====================
CREATE SEQUENCE product_attributes_id_seq START WITH 1;
CREATE SEQUENCE cms_pages_id_seq START WITH 1;
CREATE SEQUENCE cms_blocks_id_seq START WITH 1;
CREATE SEQUENCE badge_templates_id_seq START WITH 1;
CREATE SEQUENCE badge_flow_id_seq START WITH 1;

CREATE TABLE product_attributes (
    id           BIGINT PRIMARY KEY,
    name         VARCHAR(120) NOT NULL,
    code         VARCHAR(60) NOT NULL,
    type         VARCHAR(20) NOT NULL,
    options      VARCHAR(2000),
    status       VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    display_type VARCHAR(20) NOT NULL DEFAULT 'TEXT',
    created_id   BIGINT,
    created_date TIMESTAMP NOT NULL,
    updated_id   BIGINT,
    updated_date TIMESTAMP,
    CONSTRAINT uq_product_attributes_code UNIQUE (code),
    CONSTRAINT ck_product_attributes_type CHECK (type IN ('TEXT', 'SELECT', 'MULTISELECT'))
);

CREATE TABLE cms_pages (
    id           BIGINT PRIMARY KEY,
    slug         VARCHAR(140) NOT NULL,
    title        VARCHAR(200) NOT NULL,
    status       VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    locale        VARCHAR(10) NOT NULL DEFAULT 'vi',
    is_active     BOOLEAN NOT NULL DEFAULT TRUE,
    seo           TEXT,
    target_device VARCHAR(10) NOT NULL DEFAULT 'ALL',
    schedule_start TIMESTAMP,
    schedule_end  TIMESTAMP,
    published_slug VARCHAR(140),
    published_snapshot TEXT,
    published_version INTEGER NOT NULL DEFAULT 0,
    published_at  TIMESTAMP,
    published_hash VARCHAR(64),
    created_id   BIGINT,
    created_date TIMESTAMP NOT NULL,
    updated_id   BIGINT,
    updated_date TIMESTAMP,
    deleted_id   BIGINT,
    deleted_date TIMESTAMP,
    CONSTRAINT uq_cms_pages_slug UNIQUE (slug),
    CONSTRAINT ck_cms_pages_target_device CHECK (target_device IN ('ALL', 'DESKTOP', 'MOBILE')),
    CONSTRAINT ck_cms_pages_status CHECK (status IN ('DRAFT', 'PUBLISHED'))
);

CREATE TABLE cms_blocks (
    id             BIGINT PRIMARY KEY,
    page_id        BIGINT NOT NULL REFERENCES cms_pages (id) ON DELETE CASCADE,
    type           VARCHAR(40) NOT NULL,
    sort_order     INTEGER NOT NULL DEFAULT 0,
    data           TEXT,
    target_segment VARCHAR(60),
    is_visible      BOOLEAN NOT NULL DEFAULT TRUE,
    created_id     BIGINT,
    created_date   TIMESTAMP NOT NULL,
    updated_id     BIGINT,
    updated_date   TIMESTAMP,
    CONSTRAINT ck_cms_blocks_type CHECK (type IN ('BANNER', 'PRODUCT_CAROUSEL', 'PRODUCT_LIST', 'INFO_CARDS', 'IMAGE_GALLERY', 'PRODUCT_CATEGORY_NAV', 'PRODUCT_COLLECTION_SHOWCASE', 'PRODUCT_EXPANDABLE_DESCRIPTION'))
);

CREATE TABLE badge_templates (
    id                       BIGINT PRIMARY KEY,
    name                     VARCHAR(120) NOT NULL,
    code                     VARCHAR(80) NOT NULL,
    description              VARCHAR(500),
    type                     VARCHAR(20) NOT NULL,
    badge_type               VARCHAR(20) NOT NULL,
    status                   VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    display_text             VARCHAR(60),
    default_position         VARCHAR(20) NOT NULL DEFAULT 'TOP_LEFT',
    style_config             TEXT,
    icon                     VARCHAR(255),
    image                    VARCHAR(500),
    icon_mobile              VARCHAR(255),
    image_mobile             VARCHAR(500),
    asset_meta               TEXT,
    default_priority_weight  INTEGER NOT NULL DEFAULT 0,
    created_id               BIGINT,
    created_date             TIMESTAMP NOT NULL,
    updated_id               BIGINT,
    updated_date             TIMESTAMP,
    deleted_id               BIGINT,
    deleted_date             TIMESTAMP,
    CONSTRAINT uq_badge_templates_code UNIQUE (code),
    CONSTRAINT ck_badge_templates_type CHECK (type IN ('TEXT', 'ICON', 'IMAGE', 'MINI_BANNER')),
    CONSTRAINT ck_badge_templates_badge_type CHECK (badge_type IN ('CAMPAIGN', 'BEST_SELLER', 'PRICE_DIFF', 'OUT_OF_STOCK', 'NEW_ARRIVAL', 'LOW_STOCK', 'PRE_ORDER')),
    CONSTRAINT ck_badge_templates_status CHECK (status IN ('DRAFT', 'ACTIVE', 'INACTIVE', 'ARCHIVED')),
    CONSTRAINT ck_badge_templates_position CHECK (default_position IN ('TOP_LEFT', 'TOP_CENTER', 'TOP_RIGHT', 'CENTER_LEFT', 'CENTER_RIGHT', 'BOTTOM_LEFT', 'BOTTOM_CENTER', 'BOTTOM_RIGHT', 'PRICE_LINE'))
);

CREATE TABLE badge_flow (
    id            BIGINT PRIMARY KEY,
    name          VARCHAR(150) NOT NULL,
    description   VARCHAR(500),
    status        VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    active_from   TIMESTAMP,
    active_to     TIMESTAMP,
    rule_type     VARCHAR(20) NOT NULL,
    rule_config   TEXT,
    channel       VARCHAR(20) NOT NULL DEFAULT 'ALL',
    templates     TEXT NOT NULL,
    created_id    BIGINT,
    created_date  TIMESTAMP NOT NULL,
    updated_id    BIGINT,
    updated_date  TIMESTAMP,
    CONSTRAINT ck_badge_flow_status CHECK (status IN ('DRAFT', 'ACTIVE', 'INACTIVE')),
    CONSTRAINT ck_badge_flow_rule_type CHECK (rule_type IN ('MANUAL', 'ALL', 'CATEGORY', 'COLLECTION', 'PROMOTION', 'OUT_OF_STOCK')),
    CONSTRAINT ck_badge_flow_channel CHECK (channel IN ('ALL', 'WEB', 'MOBILE_WEB', 'APP'))
);

-- ===================== OTP SỐ ĐIỆN THOẠI =====================
CREATE TABLE phone_otps (
    id                       BIGINT PRIMARY KEY,
    phone                    VARCHAR(20) NOT NULL,
    purpose                  VARCHAR(20) NOT NULL,
    code_hash                VARCHAR(64) NOT NULL,
    expires_at               TIMESTAMP NOT NULL,
    attempts                 INT NOT NULL DEFAULT 0,
    verified_at              TIMESTAMP,
    registration_token_hash  VARCHAR(64),
    registration_expires_at  TIMESTAMP,
    consumed_at              TIMESTAMP,
    created_id               BIGINT,
    created_date             TIMESTAMP NOT NULL,
    updated_id               BIGINT,
    updated_date             TIMESTAMP,
    CONSTRAINT ck_phone_otps_purpose CHECK (purpose IN ('REGISTER', 'RESET_PASSWORD'))
);

-- ===================== GIỎ HÀNG & YÊU THÍCH =====================
CREATE TABLE carts (
    id            BIGINT PRIMARY KEY,
    user_id       BIGINT REFERENCES users (id) ON DELETE CASCADE,
    guest_id      VARCHAR(64),
    created_id    BIGINT,
    created_date  TIMESTAMP NOT NULL,
    updated_id    BIGINT,
    updated_date  TIMESTAMP,
    CONSTRAINT uq_carts_user UNIQUE (user_id),
    CONSTRAINT uq_carts_guest UNIQUE (guest_id)
);

CREATE TABLE product_media (
    id            BIGINT PRIMARY KEY,
    product_id    BIGINT NOT NULL REFERENCES products (id) ON DELETE CASCADE,
    sku_id        BIGINT REFERENCES product_skus (id) ON DELETE CASCADE,
    url           VARCHAR(500) NOT NULL,
    alt           VARCHAR(200),
    media_type    VARCHAR(10) NOT NULL DEFAULT 'IMAGE',
    sort_order    INTEGER NOT NULL DEFAULT 0,
    is_primary    BOOLEAN NOT NULL DEFAULT FALSE,
    created_id    BIGINT,
    created_date  TIMESTAMP NOT NULL,
    updated_id    BIGINT,
    updated_date  TIMESTAMP,
    CONSTRAINT ck_product_media_type CHECK (media_type IN ('IMAGE', 'VIDEO'))
);

CREATE TABLE product_infos (
    id            BIGINT PRIMARY KEY,
    product_id    BIGINT NOT NULL REFERENCES products (id) ON DELETE CASCADE,
    title         VARCHAR(200),
    description   VARCHAR(4000) NOT NULL,
    sort_order    INTEGER NOT NULL DEFAULT 0,
    created_id    BIGINT,
    created_date  TIMESTAMP NOT NULL
);
CREATE INDEX ix_product_infos_product ON product_infos (product_id, sort_order);

CREATE TABLE product_approval_requests (
    id              BIGINT PRIMARY KEY ,
    product_id      BIGINT NOT NULL REFERENCES products (id) ON DELETE CASCADE,
    action_type     VARCHAR(20) NOT NULL,
    status          VARCHAR(12) NOT NULL DEFAULT 'PENDING',
    submitted_by    BIGINT,
    submitted_at    TIMESTAMP NOT NULL,
    reviewed_by     BIGINT,
    reviewed_at     TIMESTAMP,
    rejected_reason VARCHAR(500),
    before_snapshot TEXT,
    after_snapshot  TEXT,
    created_id      BIGINT,
    created_date    TIMESTAMP NOT NULL,
    updated_id      BIGINT,
    updated_date    TIMESTAMP,
    CONSTRAINT ck_par_action CHECK (action_type IN ('CREATE_PRODUCT', 'UPDATE_PRODUCT')),
    CONSTRAINT ck_par_status CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'CANCELLED'))
);
CREATE INDEX ix_par_product ON product_approval_requests (product_id, id);
CREATE INDEX ix_par_status ON product_approval_requests (status);


CREATE TABLE category_attributes (
    id            BIGINT PRIMARY KEY,
    category_id   BIGINT NOT NULL REFERENCES categories (id) ON DELETE CASCADE,
    attribute_id  BIGINT NOT NULL REFERENCES product_attributes (id) ON DELETE CASCADE,
    created_id    BIGINT,
    created_date  TIMESTAMP NOT NULL,
    CONSTRAINT uq_category_attributes UNIQUE (category_id, attribute_id)
);

CREATE TABLE product_attribute_values (
    id            BIGINT PRIMARY KEY,
    attribute_id  BIGINT NOT NULL REFERENCES product_attributes (id) ON DELETE CASCADE,
    code          VARCHAR(255) NOT NULL,
    value         VARCHAR(255) NOT NULL,
    image_url     VARCHAR(500),
    sort_order    INTEGER NOT NULL DEFAULT 0,
    created_id    BIGINT,
    created_date  TIMESTAMP NOT NULL,
    updated_id    BIGINT,
    updated_date  TIMESTAMP,
    CONSTRAINT uq_product_attribute_values_code UNIQUE (attribute_id, code),
    CONSTRAINT uq_product_attribute_values_value UNIQUE (attribute_id, value)
);

CREATE TABLE product_mix_matches (
    id                  BIGINT PRIMARY KEY,
    product_id          BIGINT NOT NULL REFERENCES products (id) ON DELETE CASCADE,
    related_product_id  BIGINT NOT NULL REFERENCES products (id) ON DELETE CASCADE,
    sort_order          INTEGER NOT NULL DEFAULT 0,
    created_id          BIGINT,
    created_date        TIMESTAMP NOT NULL,
    CONSTRAINT uq_product_mix_matches UNIQUE (product_id, related_product_id),
    CONSTRAINT ck_product_mix_matches_self CHECK (product_id <> related_product_id)
);
CREATE INDEX ix_product_mix_matches_product ON product_mix_matches (product_id, sort_order);

CREATE TABLE sku_attribute_values (
    id            BIGINT PRIMARY KEY,
    sku_id        BIGINT NOT NULL REFERENCES product_skus (id) ON DELETE CASCADE,
    attribute_id  BIGINT NOT NULL REFERENCES product_attributes (id) ON DELETE CASCADE,
    value         VARCHAR(1000) NOT NULL,
    created_id    BIGINT,
    created_date  TIMESTAMP NOT NULL,
    updated_id    BIGINT,
    updated_date  TIMESTAMP,
    CONSTRAINT uq_sku_attribute_values UNIQUE (sku_id, attribute_id)
);

CREATE TABLE cart_items (
    id            BIGINT PRIMARY KEY,
    cart_id       BIGINT NOT NULL REFERENCES carts (id) ON DELETE CASCADE,
    sku_id        BIGINT NOT NULL REFERENCES product_skus (id) ON DELETE CASCADE,
    quantity      INT NOT NULL,
    created_id    BIGINT,
    created_date  TIMESTAMP NOT NULL,
    updated_id    BIGINT,
    updated_date  TIMESTAMP,
    CONSTRAINT uq_cart_items_cart_sku UNIQUE (cart_id, sku_id),
    CONSTRAINT ck_cart_items_quantity CHECK (quantity >= 1)
);

CREATE TABLE wishlist_items (
    id            BIGINT PRIMARY KEY,
    user_id       BIGINT NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    product_id    BIGINT NOT NULL REFERENCES products (id) ON DELETE CASCADE,
    created_id    BIGINT,
    created_date  TIMESTAMP NOT NULL,
    CONSTRAINT uq_wishlist_items_user_product UNIQUE (user_id, product_id)
);

CREATE TABLE banner_placements (
    id            BIGINT PRIMARY KEY,
    code          VARCHAR(60) NOT NULL,
    name          VARCHAR(150) NOT NULL,
    display_type  VARCHAR(30) NOT NULL DEFAULT 'CAROUSEL',
    platform      VARCHAR(10) NOT NULL DEFAULT 'ALL',
    status        VARCHAR(10) NOT NULL DEFAULT 'ACTIVE',
    settings      VARCHAR(2000),
    created_id    BIGINT,
    created_date  TIMESTAMP NOT NULL,
    CONSTRAINT uq_banner_placements_code UNIQUE (code)
);

CREATE TABLE banners (
    id                       BIGINT PRIMARY KEY,
    internal_name            VARCHAR(150) NOT NULL,
    media_url                VARCHAR(500),
    media_mobile_url         VARCHAR(500),
    media_link_url           VARCHAR(500),
    media_poster_url         VARCHAR(500),
    media_mobile_poster_url  VARCHAR(500),
    media_fit                VARCHAR(20) NOT NULL DEFAULT 'cover',
    media_type               VARCHAR(20) NOT NULL DEFAULT 'IMAGE',
    layout                   VARCHAR(20) NOT NULL DEFAULT 'CENTER',
    title                    VARCHAR(200),
    subtitle                 VARCHAR(300),
    title_color              VARCHAR(30),
    actions_layout           VARCHAR(20) NOT NULL DEFAULT 'STACK',
    actions                  VARCHAR(4000),
    overlay_opacity          NUMERIC(3, 2) NOT NULL DEFAULT 0,
    status                   VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    placement_code           VARCHAR(60),
    sort_order               INTEGER NOT NULL DEFAULT 0,
    created_id               BIGINT,
    created_date             TIMESTAMP NOT NULL,
    updated_id               BIGINT,
    updated_date             TIMESTAMP,
    deleted_id               BIGINT,
    deleted_date             TIMESTAMP
);

CREATE TABLE banner_placement_layouts (
    id            BIGINT PRIMARY KEY,
    placement_id  BIGINT NOT NULL REFERENCES banner_placements (id) ON DELETE CASCADE,
    name          VARCHAR(150) NOT NULL,
    status        VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    active_from   TIMESTAMP,
    active_to     TIMESTAMP,
    note          VARCHAR(500),
    created_id    BIGINT,
    created_date  TIMESTAMP NOT NULL,
    updated_id    BIGINT,
    updated_date  TIMESTAMP,
    CONSTRAINT ck_banner_layouts_status CHECK (status IN ('DRAFT', 'SCHEDULED', 'ACTIVE', 'INACTIVE', 'ARCHIVED'))
);
CREATE INDEX ix_banner_layouts_placement ON banner_placement_layouts (placement_id, status);

CREATE TABLE banner_placement_layout_items (
    id                BIGINT PRIMARY KEY,
    layout_id         BIGINT NOT NULL REFERENCES banner_placement_layouts (id) ON DELETE CASCADE,
    slot_key          VARCHAR(60) NOT NULL,
    banner_id         BIGINT NOT NULL REFERENCES banners (id),
    is_active         BOOLEAN NOT NULL DEFAULT TRUE,
    priority          INTEGER NOT NULL DEFAULT 0,
    display_metadata  VARCHAR(4000),
    created_id        BIGINT,
    created_date      TIMESTAMP NOT NULL,
    updated_id        BIGINT,
    updated_date      TIMESTAMP,
    CONSTRAINT uq_banner_layout_items UNIQUE (layout_id, slot_key, banner_id)
);
CREATE INDEX ix_banner_layout_items_layout ON banner_placement_layout_items (layout_id, slot_key, priority);

CREATE TABLE customer_requests (
    id                          BIGINT PRIMARY KEY,
    type                        VARCHAR(20) NOT NULL,
    contact_channel             VARCHAR(10) NOT NULL,
    contact_value               VARCHAR(255) NOT NULL,
    status                      VARCHAR(20) NOT NULL DEFAULT 'NEW',
    product_id                  BIGINT REFERENCES products (id) ON DELETE SET NULL,
    sku_id                      BIGINT REFERENCES product_skus (id) ON DELETE SET NULL,
    sku_code                    VARCHAR(64),
    product_name_snapshot       VARCHAR(200),
    product_image_url_snapshot  VARCHAR(500),
    variant_text_snapshot       VARCHAR(300),
    order_id                    BIGINT REFERENCES orders (id) ON DELETE SET NULL,
    order_code                  VARCHAR(60),
    order_type                  VARCHAR(20),
    created_id                  BIGINT,
    created_date                TIMESTAMP NOT NULL,
    updated_id                  BIGINT,
    updated_date                TIMESTAMP
);

CREATE TABLE pre_order_configs (
    id            BIGINT PRIMARY KEY,
    enabled       BOOLEAN NOT NULL,
    message       VARCHAR(500),
    scope_type    VARCHAR(20) NOT NULL DEFAULT 'ALL',
    scope_ids     VARCHAR(2000),
    created_id    BIGINT,
    created_date  TIMESTAMP NOT NULL,
    CONSTRAINT ck_pre_order_scope_type CHECK (scope_type IN ('ALL', 'SKU', 'PRODUCT', 'CATEGORY', 'COLLECTION'))
);

CREATE TABLE import_export_jobs (
    id              BIGINT PRIMARY KEY,
    job_type        VARCHAR(10) NOT NULL,
    entity_type     VARCHAR(20) NOT NULL,
    status          VARCHAR(12) NOT NULL,
    file_name       VARCHAR(255),
    file_url        VARCHAR(500),
    error_message   VARCHAR(1000),
    total_rows      INTEGER NOT NULL,
    success_rows    INTEGER NOT NULL,
    failed_rows     INTEGER NOT NULL,
    reference_type  VARCHAR(50),
    reference_id    VARCHAR(100),
    params          TEXT,
    started_at      TIMESTAMP,
    finished_at     TIMESTAMP,
    created_id      BIGINT,
    created_date    TIMESTAMP NOT NULL,
    updated_id      BIGINT,
    updated_date    TIMESTAMP
);
CREATE TABLE import_job_rows (
    id            BIGINT PRIMARY KEY,
    job_id        BIGINT NOT NULL REFERENCES import_export_jobs (id) ON DELETE CASCADE,
    line_no       INTEGER NOT NULL,
    status        VARCHAR(8) NOT NULL,
    message       VARCHAR(1000),
    data          TEXT,
    created_id    BIGINT,
    created_date  TIMESTAMP NOT NULL
);

CREATE TABLE order_idempotency_keys (
    id           BIGINT PRIMARY KEY,
    owner        VARCHAR(64)  NOT NULL,
    idem_key     VARCHAR(100) NOT NULL,
    request_hash VARCHAR(64)  NOT NULL,
    order_code   VARCHAR(40)  NOT NULL,
    created_id   BIGINT,
    created_date TIMESTAMP    NOT NULL,
    CONSTRAINT uq_order_idem UNIQUE (owner, idem_key)
);

CREATE TABLE site_headers (
    id                 BIGINT PRIMARY KEY,
    banner_name        VARCHAR(150) NOT NULL,
    display_style      VARCHAR(10)  NOT NULL,
    content            VARCHAR(1000),
    bg_color           VARCHAR(30),
    text_color         VARCHAR(30),
    target_url         VARCHAR(500),
    animation_style    VARCHAR(30),
    loop_interval_sec  INTEGER,
    start_time         TIMESTAMP    NOT NULL,
    end_time           TIMESTAMP,
    status             VARCHAR(12)  NOT NULL,
    created_id         BIGINT,
    created_date       TIMESTAMP    NOT NULL,
    updated_id         BIGINT,
    updated_date       TIMESTAMP,
    deleted_id         BIGINT,
    deleted_date       TIMESTAMP
);

CREATE TABLE site_footer (
    id                       BIGINT PRIMARY KEY,
    copyright_text           VARCHAR(500),
    footer_logo_url          VARCHAR(500),
    certification_image_url  VARCHAR(500),
    certification_url        VARCHAR(500),
    social_links             TEXT,
    menu_columns             TEXT,
    created_id               BIGINT,
    created_date             TIMESTAMP NOT NULL,
    updated_id               BIGINT,
    updated_date             TIMESTAMP,
    deleted_id               BIGINT,
    deleted_date             TIMESTAMP
);

CREATE TABLE site_logos (
    id               BIGINT PRIMARY KEY,
    name             VARCHAR(150) NOT NULL,
    type             VARCHAR(10)  NOT NULL,
    status           VARCHAR(12)  NOT NULL,
    logo_url         VARCHAR(500) NOT NULL,
    logo_theme       VARCHAR(30),
    logo_target_url  VARCHAR(500),
    created_id       BIGINT,
    created_date     TIMESTAMP    NOT NULL,
    updated_id       BIGINT,
    updated_date     TIMESTAMP,
    deleted_id       BIGINT,
    deleted_date     TIMESTAMP
);
